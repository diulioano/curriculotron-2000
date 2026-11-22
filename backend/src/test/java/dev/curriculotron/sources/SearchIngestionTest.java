package dev.curriculotron.sources;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.NewTaskRun;
import dev.curriculotron.orchestration.domain.Routine;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Search ingestion paths that share the assessment pipeline, isolated from selection counts. */
class SearchIngestionTest extends AppTestBase {
  @MockitoBean JobSourceFactory factory;
  @Autowired MockMvc mvc;
  @Autowired SearchService search;
  @Autowired TaskRunService tasks;
  @Autowired EntityManager em;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired PublicationStore publications;
  @Autowired SelectionRepository selectionRows;
  @Autowired CompatibilityRepository assessments;
  @Autowired AppClock clock;
  @Autowired WriteExecutor writer;

  @BeforeEach
  void resetCriteria() {
    writer.run(() -> {
      em.createNativeQuery(
              """
              UPDATE search_criteria
              SET version = 1,
                  target_roles = '["Desenvolvedor Fullstack Pleno Java","Desenvolvedor Backend Pleno Java","Software Engineer Java"]',
                  stack_keywords = '["Java","Java 17","Spring Boot","Angular","React","REST","Kafka","Microsserviços","JUnit","Mockito"]'
              WHERE id = 1
              """)
          .executeUpdate();
      em.createNativeQuery(
              """
              UPDATE compatibility_refresh
              SET target_criteria_version = 1,
                  evaluator_version = 2,
                  run_id = NULL,
                  state = 'ready',
                  started_at = NULL,
                  finished_at = NULL,
                  error_code = NULL,
                  assessed_count = 0,
                  remaining_count = 0
              WHERE id = 1
              """)
          .executeUpdate();
    });
  }

  @Test
  void shouldNotPublishOrSelectAnItemWhoseExternalIdentityConflictsWithAnotherUrl() {
    String a =
        ingestion.ingest(
            new JobSource.Item(
                "cwi:oportunidades",
                "conflict-a",
                "https://search-conflict.test/a",
                "Requirements: Java",
                "Backend Developer",
                "Remote"),
            "CWI");
    String b =
        ingestion.ingest(
            new JobSource.Item(
                "cwi:oportunidades",
                "conflict-b",
                "https://search-conflict.test/b",
                "Requirements: Java",
                "Backend Developer",
                "Remote"),
            "CWI");
    var originalDate = clock.localDate().minusDays(10);
    publications.record(a, originalDate);
    publications.record(b, originalDate.minusDays(1));
    JobSource empty = () -> new JobSource.SourceResult(List.of(), List.of(), List.of());
    when(factory.forAdapter(anyString())).thenReturn(empty);
    JobSource conflict =
        () ->
            new JobSource.SourceResult(
                List.of(
                    new JobSource.Item(
                        "cwi:oportunidades",
                        "conflict-a",
                        "https://search-conflict.test/b",
                        "Requirements: Java",
                        "Conflicting title",
                        "Remote",
                        clock.localDate())),
                List.of(),
                List.of());
    when(factory.forAdapter("cwi:oportunidades")).thenReturn(conflict);
    var run =
        tasks
            .createOrGet(NewTaskRun.system("search", TaskRunTrigger.manual, "conflict-search"))
            .run();
    search.execute(run.id());
    assertThat(publications.find(a)).isEqualTo(originalDate.toString());
    assertThat(publications.find(b)).isEqualTo(originalDate.minusDays(1).toString());
    assertThat(selectionRows.selected(clock.localDate())).doesNotContain(a, b);
  }

  @Test
  void juniorCountsOnlyAtCompaniesWithUserJustification() {
    String sap = ingestion.ingest(juniorItem("junior-sap"), "SAP");
    String bv = ingestion.ingest(juniorItem("junior-bv"), "BV");

    var sapAssessment = assessments.find(sap).orElseThrow();
    assertThat(sapAssessment.fit()).isEqualTo("strong");
    assertThat(sapAssessment.fitReasons()).anyMatch(r -> r.contains("Justificativa para júnior"));
    assertThat(assessments.find(bv).orElseThrow().fit()).isEqualTo("weak");
  }

  @Test
  void shouldAssessManualAndRoutineSearchesTheSameWayAndNeverCollectOnGetOrRefresh()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var session = TestSession.login(mvc, TestSession.PASSWORD);

    JobSource empty = () -> new JobSource.SourceResult(List.of(), List.of(), List.of());
    when(factory.forAdapter(anyString())).thenReturn(empty);

    when(factory.forAdapter("cwi:oportunidades")).thenReturn(mixedBatch("parity-manual"));
    var manualResponse =
        mvc.perform(
                post("/api/searches")
                    .session(session.session())
                    .header("X-CSRF-Token", session.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isAccepted())
            .andReturn();
    String manualRunId =
        JsonPath.read(manualResponse.getResponse().getContentAsString(), "$.taskRunId");
    awaitCompleted(manualRunId);

    when(factory.forAdapter("cwi:oportunidades")).thenReturn(mixedBatch("parity-routine"));
    String routineKey = "search:routine-parity";
    Routine routine =
        Routine.daily("r-parity", "search", null, LocalTime.of(8, 0), clock.now().minusSeconds(60));
    search.run(routine, clock.localDate(), routineKey);
    String routineRunId =
        tasks.recent(20).stream()
            .filter(
                r -> r.trigger() == TaskRunTrigger.routine && routineKey.equals(r.idempotencyKey()))
            .findFirst()
            .orElseThrow()
            .id();
    awaitCompleted(routineRunId);

    for (String kind : List.of("match", "role-mismatch", "tech-mismatch", "partial")) {
      String manualOppId = idOf("parity-manual-" + kind);
      String routineOppId = idOf("parity-routine-" + kind);
      var byManual = assessments.find(manualOppId).orElseThrow();
      var byRoutine = assessments.find(routineOppId).orElseThrow();
      assertThat(byRoutine.roleStatus()).isEqualTo(byManual.roleStatus());
      assertThat(byRoutine.technologyStatus()).isEqualTo(byManual.technologyStatus());
      assertThat(byRoutine.reasonCodes()).isEqualTo(byManual.reasonCodes());
      assertThat(byRoutine.fit()).isEqualTo(byManual.fit());
      assertThat(byRoutine.fitScore()).isEqualTo(byManual.fitScore());
      assertThat(byRoutine.fitReasons()).isEqualTo(byManual.fitReasons());
      assertThat(byRoutine.evidenceItems()).hasSameSizeAs(byManual.evidenceItems());
    }

    var selectedIds = selectionRows.selected(clock.localDate());
    assertThat(selectedIds)
        .contains(idOf("parity-manual-match"), idOf("parity-routine-match"))
        .doesNotContain(
            idOf("parity-manual-role-mismatch"),
            idOf("parity-routine-role-mismatch"),
            idOf("parity-manual-tech-mismatch"),
            idOf("parity-routine-tech-mismatch"),
            idOf("parity-manual-partial"),
            idOf("parity-routine-partial"));

    clearInvocations(factory);

    mvc.perform(get("/api/criteria").session(session.session())).andExpect(status().isOk());
    mvc.perform(get("/api/opportunities/compatibility-status").session(session.session()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/home").session(session.session())).andExpect(status().isOk());
    mvc.perform(get("/api/opportunities?list=selection").session(session.session()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/opportunities?list=secondary").session(session.session()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/opportunities/" + idOf("parity-manual-match")).session(session.session()))
        .andExpect(status().isOk());

    String updatePayload =
        """
        {"version":1,"targetRoles":["Desenvolvedor Backend Pleno"],"stackKeywords":["Python"],
         "remotePreferred":true,"hybridRegions":[],"cltMinMonthlyBrl":5000,"pjMinMonthlyBrl":7000,
         "acceptJuniorIfJustified":true,"dailySearchTimeLocal":"08:00","deadlineHighlightDays":5,
         "urgentHours":24,"emailDigestEnabled":false}
        """;
    mvc.perform(
            put("/api/criteria")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));

    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"));

    mvc.perform(get("/api/opportunities/compatibility-status").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"));

    verifyNoInteractions(factory);
  }

  private void awaitCompleted(String runId) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      var run = tasks.find(runId).orElseThrow();
      if (run.state() == TaskRunState.completed) {
        return;
      }
      if (run.state() == TaskRunState.failed || run.state() == TaskRunState.paused) {
        fail("Expected completed run %s, got %s (%s)", runId, run.state(), run.errorSummary());
      }
      Thread.sleep(20);
    }
    fail("Timed out waiting for TaskRun " + runId);
  }

  private JobSource mixedBatch(String prefix) {
    return () ->
        new JobSource.SourceResult(
            List.of(
                new JobSource.Item(
                    "cwi:oportunidades",
                    prefix + "-match",
                    "https://parity.test/" + prefix + "/match",
                    "Requisitos: Java 17, Spring Boot",
                    "Desenvolvedor(a) Java",
                    "Remoto",
                    clock.localDate(),
                    Completeness.complete,
                    null),
                new JobSource.Item(
                    "cwi:oportunidades",
                    prefix + "-role-mismatch",
                    "https://parity.test/" + prefix + "/role-mismatch",
                    "Requisitos: Java 17, Figma",
                    "Service Designer Sênior",
                    "Remoto",
                    clock.localDate(),
                    Completeness.complete,
                    null),
                new JobSource.Item(
                    "cwi:oportunidades",
                    prefix + "-tech-mismatch",
                    "https://parity.test/" + prefix + "/tech-mismatch",
                    "Requisitos completos: Salesforce Apex, Lightning Web Components",
                    "Desenvolvedor Backend Pleno",
                    "Remoto",
                    clock.localDate(),
                    Completeness.complete,
                    null),
                new JobSource.Item(
                    "cwi:oportunidades",
                    prefix + "-partial",
                    "https://parity.test/" + prefix + "/partial",
                    "Atuação com APIs de pagamentos",
                    "Desenvolvedor Backend Pleno",
                    "Remoto",
                    clock.localDate(),
                    Completeness.partial,
                    "Resumo da listagem")),
            List.of(),
            List.of());
  }

  private String idOf(String externalId) {
    return (String)
        em.createNativeQuery(
                "SELECT opportunity_id FROM opportunity_source WHERE external_id=:external")
            .setParameter("external", externalId)
            .getSingleResult();
  }

  private static JobSource.Item juniorItem(String externalId) {
    return new JobSource.Item(
        "test",
        externalId,
        "https://example.com/vaga/" + externalId,
        "Desenvolvedor Java Júnior\nRemoto",
        "Desenvolvedor Java Júnior",
        "Remoto");
  }
}
