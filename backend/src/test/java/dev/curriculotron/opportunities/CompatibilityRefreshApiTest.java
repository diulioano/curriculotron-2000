package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.curriculotron.opportunities.adapter.in.web.CompatibilityRefreshController;
import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.AssessmentInputService;
import dev.curriculotron.opportunities.application.CompatibilityConflictException;
import dev.curriculotron.opportunities.application.CompatibilityConflictException.Reason;
import dev.curriculotron.opportunities.application.CompatibilityRefreshService;
import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.ReassessOpportunitiesService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.shared.web.ApiErrorHandler;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompatibilityRefreshApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired CompatibilityRefreshService refreshService;
  @Autowired CompatibilityRepository compatibilityRepository;
  @Autowired AssessmentInputService inputService;
  @Autowired AssessOpportunityService assessor;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM task_run").executeUpdate();
      em.createNativeQuery("DELETE FROM integration_status").executeUpdate();
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_evidence").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_url_identity").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_external_identity").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_source").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_match_candidate").executeUpdate();
      em.createNativeQuery("DELETE FROM application").executeUpdate();
      em.createNativeQuery("UPDATE opportunity SET merged_into = NULL").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
      em.createNativeQuery(
              """
              UPDATE search_criteria
              SET version = 1,
                  target_roles = '["Desenvolvedor Fullstack Pleno Java","Desenvolvedor Backend Pleno Java"]',
                  stack_keywords = '["Java","Spring Boot"]'
              WHERE id = 1
              """)
          .executeUpdate();
      em.createNativeQuery(
              """
              UPDATE compatibility_refresh
              SET target_criteria_version = 1,
                  evaluator_version = 1,
                  run_id = NULL,
                  state = 'pending',
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
  void shouldEnforceSessionCsrfLiteralRouteAndReadOnlyStatus() throws Exception {
    mvc.perform(get("/api/opportunities/compatibility-status"))
        .andExpect(status().isUnauthorized());

    TestSession.setup(mvc, TestSession.PASSWORD);
    var anon = TestSession.anonymous(mvc);
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(anon.session())
                .header("X-CSRF-Token", anon.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":1}"))
        .andExpect(status().isUnauthorized());

    var session = TestSession.login(mvc, TestSession.PASSWORD);

    // Authenticated POST without CSRF must be rejected with 403.
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":1}"))
        .andExpect(status().isForbidden());

    ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            "java-pending-1",
            "https://jobs.lever.co/ciandt/java-pending-1",
            "Requisitos: Java 17 e Spring Boot.",
            "Desenvolvedor Backend Pleno Java",
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        "CI&T");
    // Delete assessment to simulate unassessed/stale backlog before GET status.
    writer.run(() -> em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate());

    // Literal route /compatibility-status must win over /{id} and never trigger
    // collection/reassess.
    mvc.perform(get("/api/opportunities/compatibility-status").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criteriaVersion").value(1))
        .andExpect(jsonPath("$.state").value("pending"))
        .andExpect(jsonPath("$.stale").value(true))
        .andExpect(jsonPath("$.assessedCount").value(0))
        .andExpect(jsonPath("$.remainingCount").value(1))
        .andExpect(jsonPath("$.errorCode").isEmpty());

    mvc.perform(get("/api/opportunities/compatibility-unknown-id").session(session.session()))
        .andExpect(status().isNotFound());

    long runsAfterGet =
        ((Number) em.createNativeQuery("SELECT COUNT(*) FROM task_run").getSingleResult())
            .longValue();
    long integrationsAfterGet =
        ((Number) em.createNativeQuery("SELECT COUNT(*) FROM integration_status").getSingleResult())
            .longValue();
    assertThat(runsAfterGet).isZero();
    assertThat(integrationsAfterGet).isZero();
    assertThat(compatibilityRepository.pendingCount(1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isEqualTo(1);
  }

  @Test
  void shouldRejectInvalidCriteriaVersionPayloadsWith400() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var session = TestSession.login(mvc, TestSession.PASSWORD);

    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_criteria_version"));

    for (String payload :
        new String[] {
          "null",
          "{}",
          "{\"criteriaVersion\":null}",
          "{\"criteriaVersion\":0}",
          "{\"criteriaVersion\":-5}",
          "{\"criteriaVersion\":\"abc\"}",
          "{broken}"
        }) {
      mvc.perform(
              post("/api/opportunities/reassess")
                  .session(session.session())
                  .header("X-CSRF-Token", session.csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("invalid_criteria_version"));
    }
  }

  @Test
  void shouldReassessSynchronouslyWithoutIdempotencyKeyPreservingSelectionAndHandlingConflicts()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var session = TestSession.login(mvc, TestSession.PASSWORD);

    String javaId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "java-1",
                "https://jobs.lever.co/ciandt/java-1",
                "Requisitos: Java 17 e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    String pythonId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "python-1",
                "https://jobs.lever.co/ciandt/python-1",
                "Requisitos: Python e FastAPI.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    selections.selectToday();

    // Update criteria from Java to Python -> criteriaVersion becomes 2, state=pending.
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
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.assessment.state").value("pending"));

    // Stale criteriaVersion returns 409 criteria_version_conflict.
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("criteria_version_conflict"));

    // Valid synchronous POST without Idempotency-Key returns 200 ready (never 202).
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criteriaVersion").value(2))
        .andExpect(jsonPath("$.state").value("ready"))
        .andExpect(jsonPath("$.assessedCount").value(2))
        .andExpect(jsonPath("$.remainingCount").value(0))
        .andExpect(jsonPath("$.finishedAt").isString());

    // Repeating when already ready succeeds synchronously as idempotent no-op.
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criteriaVersion").value(2))
        .andExpect(jsonPath("$.state").value("ready"))
        .andExpect(jsonPath("$.remainingCount").value(0));

    // daily_selection keeps original selection row (reassess never selects/unselects).
    var selectedIds =
        em.createNativeQuery("SELECT opportunity_id FROM daily_selection", String.class)
            .getResultList();
    assertThat(selectedIds).containsExactly(javaId);

    // Secondary list reflects newly compatible Python job and excludes Java job.
    mvc.perform(get("/api/opportunities?list=secondary").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(pythonId));

    // Concurrent running reservation produces 409 reassessment_running.
    writer.run(() -> em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate());
    refreshService.reserve(2);
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("reassessment_running"));

    mvc.perform(get("/api/opportunities/compatibility-status").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("running"));
  }

  @Test
  void shouldInspectPersistedStatusAfterClientTimeoutWithoutAssumingCancellation()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var session = TestSession.login(mvc, TestSession.PASSWORD);

    ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            "timeout-1",
            "https://jobs.lever.co/ciandt/timeout-1",
            "Requisitos: Java 17.",
            "Desenvolvedor Backend Pleno",
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        "CI&T");
    writer.run(() -> em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate());

    CountDownLatch enteredCalculation = new CountDownLatch(1);
    CountDownLatch allowCompletion = new CountDownLatch(1);
    ReassessOpportunitiesService slowReassess =
        new ReassessOpportunitiesService(
            refreshService, compatibilityRepository, inputService, assessor, writer) {
          @Override
          public CompatibilityStatus reassess(long requestedCriteriaVersion) {
            var reservation = refreshService.reserve(requestedCriteriaVersion);
            enteredCalculation.countDown();
            try {
              if (!allowCompletion.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch timeout in test");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
            refreshService.failIfOwner(
                reservation.runId(),
                reservation.criteriaVersion(),
                reservation.evaluatorVersion(),
                "assessment_failed");
            return refreshService.status();
          }
        };

    MockMvc slowMvc =
        MockMvcBuilders.standaloneSetup(
                new CompatibilityRefreshController(slowReassess, refreshService))
            .setControllerAdvice(new ApiErrorHandler())
            .build();

    CompletableFuture<Integer> clientFuture =
        CompletableFuture.supplyAsync(() -> {
          try {
            return slowMvc
                .perform(
                    post("/api/opportunities/reassess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"criteriaVersion\":1}"))
                .andReturn()
                .getResponse()
                .getStatus();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    assertThat(enteredCalculation.await(2, TimeUnit.SECONDS)).isTrue();
    // Simulate client HTTP timeout before server completes: client does NOT assume cancellation.
    assertThatThrownBy(() -> clientFuture.get(50, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);

    // Querying real GET /api/opportunities/compatibility-status reports true persisted state.
    mvc.perform(get("/api/opportunities/compatibility-status").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("running"))
        .andExpect(jsonPath("$.stale").value(true));

    allowCompletion.countDown();
    assertThat(clientFuture.get(2, TimeUnit.SECONDS)).isEqualTo(500);

    mvc.perform(get("/api/opportunities/compatibility-status").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("failed"))
        .andExpect(jsonPath("$.errorCode").value("assessment_failed"));
  }

  @Test
  void shouldMapEvidenceChangedAndReassessmentFailuresSafelyWithoutClaimingReady()
      throws Exception {
    ReassessOpportunitiesService failingService =
        new ReassessOpportunitiesService(
            refreshService, compatibilityRepository, inputService, assessor, writer) {
          @Override
          public CompatibilityStatus reassess(long requestedCriteriaVersion) {
            if (requestedCriteriaVersion == 10) {
              throw new CompatibilityConflictException(Reason.EVIDENCE_CHANGED);
            }
            if (requestedCriteriaVersion == 11) {
              return new CompatibilityStatus(
                  11, State.failed, true, 1, 2, Instant.EPOCH, Instant.EPOCH, "assessment_failed");
            }
            if (requestedCriteriaVersion == 13) {
              throw new IllegalArgumentException(
                  "Persisted row secret path /data/curriculotron.db");
            }
            throw new IllegalStateException("SQLITE_BUSY C:/Users/secret/curriculotron.db");
          }
        };

    MockMvc isolatedMvc =
        MockMvcBuilders.standaloneSetup(
                new CompatibilityRefreshController(failingService, refreshService))
            .setControllerAdvice(new ApiErrorHandler())
            .build();

    isolatedMvc
        .perform(
            post("/api/opportunities/reassess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":10}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("evidence_changed"));

    isolatedMvc
        .perform(
            post("/api/opportunities/reassess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":11}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("reassessment_failed"));

    Logger logger = (Logger) LoggerFactory.getLogger(CompatibilityRefreshController.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      isolatedMvc
          .perform(
              post("/api/opportunities/reassess")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"criteriaVersion\":12}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error.code").value("reassessment_failed"))
          .andExpect(
              jsonPath("$.error.message")
                  .value("Falha ao reavaliar vagas. Consulte o status e tente novamente."));

      isolatedMvc
          .perform(
              post("/api/opportunities/reassess")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"criteriaVersion\":13}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error.code").value("reassessment_failed"));

      assertThat(appender.list).hasSize(2);
      ILoggingEvent first = appender.list.get(0);
      assertThat(first.getThrowableProxy()).isNull();
      assertThat(first.getFormattedMessage())
          .contains("criteriaVersion=12", "IllegalStateException")
          .doesNotContain("SQLITE_BUSY", "C:/Users/secret");

      ILoggingEvent second = appender.list.get(1);
      assertThat(second.getThrowableProxy()).isNull();
      assertThat(second.getFormattedMessage())
          .contains("criteriaVersion=13", "IllegalArgumentException")
          .doesNotContain("/data/curriculotron.db");
    } finally {
      logger.detachAppender(appender);
    }
  }
}
