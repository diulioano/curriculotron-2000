package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.applications.ApplicationService;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

class CompatibilityHistoryTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired OpportunityService jobs;
  @Autowired DailySelectionService selections;
  @Autowired SelectionRepository selectionRepository;
  @Autowired ApplicationService applications;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T14:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM company").executeUpdate();
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_evidence WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_url_identity WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_external_identity WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_source WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_match_candidate").executeUpdate();
      em.createNativeQuery(
              "UPDATE opportunity SET merged_into = NULL WHERE id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity WHERE id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              """
              UPDATE search_criteria
              SET version = 1,
                  target_roles = '["Desenvolvedor Fullstack Pleno Java","Desenvolvedor Backend Pleno Java","Software Engineer Java"]',
                  stack_keywords = '["Java","Java 17","Spring Boot"]',
                  remote_preferred = 1
              WHERE id = 1
              """)
          .executeUpdate();
      em.createNativeQuery(
              """
              UPDATE compatibility_refresh
              SET target_criteria_version = 1,
                  evaluator_version = 2,
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
  void tenConsumedRemainTenEvenWithFiveVisibleAndNeverRefillFromSecondary() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    // 5 jobs with Java+Python (higher priority via CI&T so all 5 enter top 10), 5 Java-only jobs
    // with priority CWI (so all 10 top slots are filled), plus 2 extra Java+Python jobs
    // (secondary).
    writer.run(() -> {
      em.createNativeQuery(
              "INSERT INTO company(id,name,tier,careers_adapter,created_at,updated_at) VALUES ('p1','CI&T','priority',NULL,'2026-11-15T14:00:00Z','2026-11-15T14:00:00Z')")
          .executeUpdate();
      em.createNativeQuery(
              "INSERT INTO company(id,name,tier,careers_adapter,created_at,updated_at) VALUES ('p2','CWI','priority',NULL,'2026-11-15T14:00:00Z','2026-11-15T14:00:00Z')")
          .executeUpdate();
    });

    for (int i = 1; i <= 5; i++) {
      ingestion.ingest(
          new JobSource.Item(
              "lever:ciandt",
              "keep-" + i,
              "https://history.test/keep-" + i,
              "Requisitos: Java e Python.",
              "Desenvolvedor Backend Pleno",
              "Remoto",
              clock.localDate(),
              Completeness.complete,
              null),
          "CI&T");
    }
    for (int i = 1; i <= 5; i++) {
      ingestion.ingest(
          new JobSource.Item(
              "cwi:oportunidades",
              "drop-" + i,
              "https://history.test/drop-" + i,
              "Requisitos: Java apenas.",
              "Desenvolvedor Backend Pleno",
              "Remoto",
              clock.localDate(),
              Completeness.complete,
              null),
          "CWI");
    }
    for (int i = 1; i <= 2; i++) {
      ingestion.ingest(
          new JobSource.Item(
              "board",
              "extra-" + i,
              "https://history.test/extra-" + i,
              "Requisitos: Java e Python.",
              "Desenvolvedor Backend Pleno",
              "Remoto",
              clock.localDate(),
              Completeness.complete,
              null),
          "Other Co");
    }

    List<String> selectedOrder = selections.selectToday();
    assertThat(selectedOrder).hasSize(10);

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(10))
        .andExpect(jsonPath("$.selectionToday.items.length()").value(10));

    // Change criteria Java -> Python, then explicitly reassess.
    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"targetRoles":["Desenvolvedor Backend Pleno"],
                     "stackKeywords":["Python"],"remotePreferred":true,"hybridRegions":[],
                     "cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,"acceptJuniorIfJustified":true,
                     "dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,"urgentHours":48,
                     "emailDigestEnabled":false}
                    """))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/opportunities/reassess")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"))
        .andExpect(jsonPath("$.remainingCount").value(0));

    mvc.perform(get("/api/opportunities/compatibility-status").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"))
        .andExpect(jsonPath("$.stale").value(false));

    // Only 5 remain visible in selection, 2 compatible extras appear in secondary, and
    // consumedCount
    // stays 10!
    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5));
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(10))
        .andExpect(jsonPath("$.selectionToday.items.length()").value(5));

    // Ranks 1..10 in daily_selection are bit-for-bit identical and never refilled on subsequent
    // selectToday().
    assertThat(selections.selectToday()).containsExactlyElementsOf(selectedOrder);
    assertThat(selectionRepository.selected(clock.localDate()))
        .containsExactlyElementsOf(selectedOrder);
  }

  @Test
  void reassessNeverSelectsNewVacanciesOrRepositionsExistingDailySelectionRanks() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    // Part A: Empty daily_selection stays empty after reassess even with compatible vacancies.
    String first =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "no-select-1",
                "https://reposition.test/1",
                "Requisitos: Java e Python.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Alpha");
    String second =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "no-select-2",
                "https://reposition.test/2",
                "Requisitos: Java e Python.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Beta");

    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"targetRoles":["Desenvolvedor Backend Pleno"],
                     "stackKeywords":["Python"],"remotePreferred":true,"hybridRegions":[],
                     "cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,"acceptJuniorIfJustified":true,
                     "dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,"urgentHours":48,
                     "emailDigestEnabled":false}
                    """))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/opportunities/reassess")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"));

    assertThat(selectionRepository.selected(clock.localDate())).isEmpty();
    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(0));

    // Part B: Select 2 vacancies, then ingest a higher-priority company vacancy (unselected) and
    // run reassess; ranks 1..2 must not be reordered and the 3rd must not be inserted.
    List<String> initialRanks = selections.selectToday();
    assertThat(initialRanks).containsExactly(first, second);

    writer.run(() ->
        em.createNativeQuery(
                "INSERT INTO company(id,name,tier,careers_adapter,created_at,updated_at) VALUES ('prio','Top Co','priority',NULL,'2026-11-15T14:00:00Z','2026-11-15T14:00:00Z')")
            .executeUpdate());
    String higherScoredUnselected =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "no-select-3",
                "https://reposition.test/3",
                "Requisitos: Python e Go.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Top Co");

    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":2,"targetRoles":["Desenvolvedor Backend Pleno"],
                     "stackKeywords":["Python","Go"],"remotePreferred":true,"hybridRegions":[],
                     "cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,"acceptJuniorIfJustified":true,
                     "dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,"urgentHours":48,
                     "emailDigestEnabled":false}
                    """))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/opportunities/reassess")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"));

    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(first, second);
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(higherScoredUnselected));
  }

  @Test
  void reassessPreservesDiscardedDecisionsAndAppliedApplicationsAcrossCriteriaChanges()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String discardedId =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "hist-disc",
                "https://hist-preserve.test/disc",
                "Requisitos: Java e Python.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Keep Co");
    String appliedId =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "hist-app",
                "https://hist-preserve.test/app",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Keep Co");

    selections.selectToday();
    jobs.decide(discardedId, "discarded");
    var app = applications.apply(appliedId);
    List<String> selectedSnapshot = selectionRepository.selected(clock.localDate());

    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"targetRoles":["Desenvolvedor Backend Pleno"],
                     "stackKeywords":["Python"],"remotePreferred":true,"hybridRegions":[],
                     "cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,"acceptJuniorIfJustified":true,
                     "dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,"urgentHours":48,
                     "emailDigestEnabled":false}
                    """))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/opportunities/reassess")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"));

    assertThat(jobs.get(discardedId).userDecision()).isEqualTo("discarded");
    assertThat(jobs.get(appliedId).userDecision()).isEqualTo("applied");
    assertThat(applications.detail(app.id()).opportunityId()).isEqualTo(appliedId);
    assertThat(selectionRepository.selected(clock.localDate()))
        .containsExactlyElementsOf(selectedSnapshot);
    assertThat(
            jdbc.sql("SELECT count(*) FROM status_change WHERE application_id = ?")
                .param(app.id())
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(discardedId))
        .andExpect(jsonPath("$.items[0].compatibility.current").value(true));
  }
}
