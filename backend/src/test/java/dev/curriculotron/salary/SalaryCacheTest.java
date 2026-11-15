package dev.curriculotron.salary;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.*;
import dev.curriculotron.salary.adapter.out.persistence.*;
import dev.curriculotron.salary.application.*;
import dev.curriculotron.salary.domain.*;
import dev.curriculotron.shared.*;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("local")
class SalaryCacheTest extends AppTestBase {
  @Autowired SalaryEstimateService salary;
  @Autowired SalaryEstimatePersistenceAdapter store;
  @Autowired JdbcTemplate jdbc;
  @Autowired WriteExecutor writer;
  @Autowired AppClock clock;
  @Autowired TaskRunService tasks;
  @Autowired MockMvc mvc;
  @Autowired jakarta.persistence.EntityManager em;
  private final JsonMapper json = JsonMapper.builder().build();

  private Opportunity job(String company, String contract) {
    return new Opportunity(
        Ids.newId(),
        "Desenvolvedor Java Pleno",
        company,
        "Brasil",
        "unknown",
        contract,
        null,
        List.of(),
        List.of(),
        List.of("Java"),
        "none",
        "open",
        null);
  }

  private SalaryEstimate estimate(SalaryProfile p, Instant at) {
    return new SalaryEstimate(
        Ids.newId(),
        p,
        new BigDecimal("71000"),
        new BigDecimal("90000"),
        "yearly",
        "BRL",
        4,
        "Glassdoor",
        "https://www.glassdoor.com.br/Sal%C3%A1rio/example.htm",
        at,
        null,
        "Faixa anual 71000–90000 BRL.");
  }

  private void persist(Opportunity j) {
    writer.run(() ->
        jdbc.update(
            "INSERT INTO opportunity(id,title,company_name,location,work_mode,contract_type,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
            j.id(),
            j.title(),
            j.companyName(),
            j.location(),
            j.workMode(),
            j.contractType(),
            clock.now().toString(),
            clock.now().toString()));
  }

  @AfterEach
  void resetClock() {
    clock.setClock(Clock.system(AppClock.ZONE));
  }

  @Test
  void lookupSeparatesCompaniesRegimesAndKeepsOriginalFacts() {
    var j = job("Company A", "clt");
    persist(j);
    var e = estimate(SalaryEstimateService.profile(j), clock.now());
    salary.importEstimate(e);
    var task =
        tasks
            .createOrGet(NewTaskRun.system("salary", TaskRunTrigger.manual, Ids.newId()))
            .run()
            .id();
    assertThat(salary.enrich(j, task, false)).isEqualTo("succeeded");
    assertThat(json.writeValueAsString(salary.reference(j))).contains("5916.67", "available");
    assertThat(json.writeValueAsString(salary.reference(job("Company B", "clt"))))
        .contains("unavailable");
    assertThat(json.writeValueAsString(salary.reference(job("Company A", "pj"))))
        .contains("unavailable");
    assertThat(j.salary()).isNull();
    assertThat(j.fitReasons()).containsExactly("Java");
  }

  @Test
  void expiredFailureKeepsDateAndDeduplicatesAttemptAndHistory() {
    var j = job("Expired Company", "clt");
    persist(j);
    var at = clock.now().minus(Duration.ofDays(90));
    var e = estimate(SalaryEstimateService.profile(j), at);
    salary.importEstimate(e);
    assertThat(e.stale(at.plus(Duration.ofDays(90)).minusNanos(1))).isFalse();
    assertThat(e.stale(at.plus(Duration.ofDays(90)))).isTrue();
    var task =
        tasks
            .createOrGet(NewTaskRun.system("salary", TaskRunTrigger.manual, Ids.newId()))
            .run()
            .id();
    assertThat(salary.enrich(j, task, false)).isEqualTo("not_configured");
    assertThat(salary.enrich(j, task, false)).isEqualTo("already_attempted");
    assertThat(store.latest(e.criteria()).orElseThrow().consultedAt()).isEqualTo(at);
    salary.importEstimate(estimate(e.criteria(), clock.now()));
    assertThat(store.all().stream().filter(x -> x.criteria().equals(e.criteria())).count())
        .isEqualTo(2);
  }

  @Test
  void unknownRegimeIsStoredButNeverApplied() {
    var j = job("Unknown Company", "unknown");
    persist(j);
    salary.importEstimate(estimate(SalaryEstimateService.profile(j), clock.now()));
    assertThat(json.writeValueAsString(salary.reference(j)))
        .contains("insufficient_profile")
        .doesNotContain("71000");
  }

  @Test
  void apiImportIsCsrfProtectedAndReadsAreSideEffectFree() throws Exception {
    var session = TestSession.anonymous(mvc);
    var e =
        estimate(
            new SalaryProfile(
                "API Company", "desenvolvedor java", "pleno", "brasil", "unknown", "unknown"),
            clock.now());
    mvc.perform(
            post("/api/salary-estimates/import")
                .contentType("application/json")
                .content(json.writeValueAsString(List.of(e))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/salary-estimates/import")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .header("Idempotency-Key", Ids.newId())
                .contentType("application/json")
                .content(json.writeValueAsString(List.of(e))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.imported").value(1));
    int before = store.all().size();
    mvc.perform(get("/api/salary-estimates")).andExpect(status().isOk());
    mvc.perform(get("/api/salary-estimates")).andExpect(status().isOk());
    assertThat(store.all()).hasSize(before);
  }

  @Test
  void refreshRejectsUnknownProfileAndAbsentJob() throws Exception {
    var j = job("Unknown Refresh", "unknown");
    persist(j);
    var s = TestSession.anonymous(mvc);
    mvc.perform(
            post("/api/opportunities/" + j.id() + "/salary-estimate/refresh")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .header("Idempotency-Key", Ids.newId()))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/opportunities/absent/salary-estimate/refresh")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .header("Idempotency-Key", Ids.newId()))
        .andExpect(status().isNotFound());
  }

  @Test
  void declaredSalaryPreventsReference() {
    var j = job("Declared", "clt");
    var declared =
        new Opportunity(
            j.id(),
            j.title(),
            j.companyName(),
            j.location(),
            j.workMode(),
            j.contractType(),
            new Opportunity.Salary(BigDecimal.ONE, BigDecimal.TEN, "monthly"),
            j.facts(),
            j.inferred(),
            j.fitReasons(),
            j.userDecision(),
            j.availability(),
            null);
    assertThat(salary.reference(declared)).isNull();
  }

  @Test
  void refreshTaskIsIdempotentAndPausesForAssistedLookup() throws Exception {
    var j = job("Refresh Task Company", "clt");
    persist(j);
    var s = TestSession.anonymous(mvc);
    String key = Ids.newId();
    var first =
        mvc.perform(
                post("/api/opportunities/" + j.id() + "/salary-estimate/refresh")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .header("Idempotency-Key", key))
            .andExpect(status().isAccepted())
            .andReturn();
    var repeated =
        mvc.perform(
                post("/api/opportunities/" + j.id() + "/salary-estimate/refresh")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .header("Idempotency-Key", key))
            .andExpect(status().isAccepted())
            .andReturn();
    String id =
        com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.taskRunId");
    assertThat(
            (String)
                com.jayway.jsonpath.JsonPath.read(
                    repeated.getResponse().getContentAsString(), "$.taskRunId"))
        .isEqualTo(id);
    for (int i = 0; i < 100 && tasks.find(id).orElseThrow().state() != TaskRunState.paused; i++)
      Thread.sleep(20);
    assertThat(tasks.find(id).orElseThrow().state()).isEqualTo(TaskRunState.paused);
    assertThat(tasks.find(id).orElseThrow().pauseReason()).isEqualTo(PauseReason.needs_person);
  }

  @Test
  void nullImportIsRejectedWithoutWrites() throws Exception {
    var s = TestSession.anonymous(mvc);
    int before = store.all().size();
    mvc.perform(
            post("/api/salary-estimates/import")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType("application/json")
                .content("[null]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_input"));
    assertThat(store.all()).hasSize(before);
  }
}
