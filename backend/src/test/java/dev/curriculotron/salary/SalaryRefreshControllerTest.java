package dev.curriculotron.salary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.NewTaskRun;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.salary.adapter.out.persistence.SalaryEstimatePersistenceAdapter;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.salary.application.SalaryLookupCoordinator;
import dev.curriculotron.salary.application.SalaryRefreshService;
import dev.curriculotron.salary.application.port.out.SalaryReferenceSource;
import dev.curriculotron.salary.domain.*;
import dev.curriculotron.shared.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("local")
class SalaryRefreshControllerTest extends AppTestBase {
  @MockitoBean SalaryReferenceSource source;
  @Autowired MockMvc mvc;
  @Autowired WriteExecutor writer;
  @Autowired SalaryEstimatePersistenceAdapter store;
  @Autowired JdbcTemplate jdbc;
  @Autowired SalaryEstimateService salary;
  @Autowired IntegrationStatusService integrations;
  @Autowired TaskRunService tasks;
  @Autowired AppClock clock;
  @Autowired OpportunityService jobs;
  @Autowired SalaryRefreshService refreshes;
  @Autowired SalaryLookupCoordinator coordinator;

  private String job(String company, String regime, boolean declared) {
    String id = Ids.newId();
    writer.run(() ->
        jdbc.update(
            "INSERT INTO opportunity(id,title,company_name,location,work_mode,contract_type,salary_min,salary_max,salary_unit,created_at,updated_at) VALUES (?,'Desenvolvedor Java Pleno',?,'Brasil','unknown',?,?,?,?,?,?)",
            id,
            company,
            regime,
            declared ? "6000" : null,
            declared ? "8000" : null,
            declared ? "monthly" : "unknown",
            clock.now().toString(),
            clock.now().toString()));
    return id;
  }

  private String url(String id) {
    return "/api/opportunities/" + id + "/salary-estimate/refresh";
  }

  @Test
  void manualRefreshSharesScheduledLookupWhileOtherProfilesProceed() throws Exception {
    String first = job("Scheduled overlap", "clt", false);
    String second = job("Independent profile", "clt", false);
    var firstEntered = new CountDownLatch(1);
    var bothEntered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(source.lookup(any()))
        .thenAnswer(invocation -> {
          firstEntered.countDown();
          bothEntered.countDown();
          if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout");
          return new SalaryReferenceSource.Result("blocked", null);
        });
    var scheduled =
        tasks.createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, Ids.newId())).run();
    tasks.start(scheduled.id());
    String requestKey = Ids.newId();
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var routine = workers.submit(() -> salary.enrich(jobs.get(first), scheduled.id(), false));
      String otherTask;
      try {
        assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(refreshes.refresh(jobs.get(first), requestKey).taskRunId())
            .isEqualTo(scheduled.id());
        otherTask = refreshes.refresh(jobs.get(second), requestKey).taskRunId();
        assertThat(otherTask).isNotEqualTo(scheduled.id());
        assertThat(bothEntered.await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }
      assertThat(routine.get(5, TimeUnit.SECONDS)).isEqualTo("blocked");
      assertThat(refreshes.refresh(jobs.get(first), requestKey).taskRunId())
          .isEqualTo(scheduled.id());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (tasks.find(otherTask).orElseThrow().state() != TaskRunState.paused
          && System.nanoTime() < deadline) Thread.sleep(10);
      assertThat(tasks.find(otherTask).orElseThrow().state()).isEqualTo(TaskRunState.paused);
      verify(source, times(2)).lookup(any());
    }
  }

  @Test
  void rejectedSubmissionFailsTaskAndReleasesProfile() {
    var job = jobs.get(job("Rejected submission", "clt", false));
    var stopped = new SalaryRefreshService(salary, tasks, coordinator, store, writer);
    stopped.close();
    var result = stopped.refresh(job, Ids.newId());
    assertThat(tasks.find(result.taskRunId()).orElseThrow().state()).isEqualTo(TaskRunState.failed);
    var claim = coordinator.reserve(SalaryEstimateService.profile(job).key(), () -> "probe");
    try {
      assertThat(claim.owner()).isTrue();
    } finally {
      coordinator.release(SalaryEstimateService.profile(job).key(), claim.lookup());
    }
    verifyNoInteractions(source);
  }

  @Test
  void validatesCsrfIdempotencyHeaderAndJobPreconditions() throws Exception {
    var session = TestSession.anonymous(mvc);
    String valid = job("Valid", "clt", false);
    mvc.perform(post(url(valid)).session(session.session()).header("Idempotency-Key", Ids.newId()))
        .andExpect(status().isForbidden());
    mvc.perform(post(url(valid)).session(session.session()).header("X-CSRF-Token", session.csrf()))
        .andExpect(status().isBadRequest());
    for (var entry :
        java.util.Map.of(
                "absent",
                404,
                job("Declared", "clt", true),
                409,
                job("Unknown", "unknown", false),
                422)
            .entrySet()) {
      mvc.perform(
              post(url(entry.getKey()))
                  .session(session.session())
                  .header("X-CSRF-Token", session.csrf())
                  .header("Idempotency-Key", Ids.newId()))
          .andExpect(status().is(entry.getValue()));
    }
    verifyNoInteractions(source);
  }

  @Test
  void concurrentRefreshReusesTaskAndFailedLookupPreservesReferenceAndLastSuccess()
      throws Exception {
    String id = job("Concurrent Company", "clt", false);
    String other = job("Concurrent Company", "clt", false);
    var p =
        new SalaryProfile(
            "Concurrent Company", "desenvolvedor java", "pleno", "Brasil", "unknown", "clt");
    var old =
        new SalaryEstimate(
            Ids.newId(),
            p,
            new BigDecimal("6000"),
            new BigDecimal("8000"),
            "monthly",
            "BRL",
            10,
            "Glassdoor",
            "https://www.glassdoor.com.br/",
            clock.now().minus(Duration.ofDays(91)),
            null,
            "Base mensal 6000 a 8000 BRL");
    salary.importEstimate(old);
    integrations.recordSuccess("source:salary", "Earlier successful lookup");
    var lastSuccess = integrations.find("source:salary").orElseThrow().lastSuccessAt();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(source.lookup(any()))
        .thenAnswer(invocation -> {
          entered.countDown();
          if (!release.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("Test did not release source");
          return new SalaryReferenceSource.Result("blocked", null);
        });
    var session = TestSession.anonymous(mvc);
    String key = Ids.newId();
    String task;
    try {
      var first =
          mvc.perform(
                  post(url(id))
                      .session(session.session())
                      .header("X-CSRF-Token", session.csrf())
                      .header("Idempotency-Key", key))
              .andExpect(status().isAccepted())
              .andReturn();
      task =
          com.jayway.jsonpath.JsonPath.read(
              first.getResponse().getContentAsString(), "$.taskRunId");
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      mvc.perform(
              post(url(other))
                  .session(session.session())
                  .header("X-CSRF-Token", session.csrf())
                  .header("Idempotency-Key", Ids.newId()))
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.taskRunId").value(task));
    } finally {
      release.countDown();
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (tasks.find(task).orElseThrow().state() != TaskRunState.paused
        && System.nanoTime() < deadline) Thread.sleep(10);
    assertThat(tasks.find(task).orElseThrow().state()).isEqualTo(TaskRunState.paused);
    mvc.perform(
            post(url(id))
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .header("Idempotency-Key", key))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.taskRunId").value(task));
    verify(source, times(1)).lookup(p);
    assertThat(store.latest(p).orElseThrow()).isEqualTo(old);
    assertThat(integrations.find("source:salary").orElseThrow().lastSuccessAt())
        .isEqualTo(lastSuccess);
  }
}
