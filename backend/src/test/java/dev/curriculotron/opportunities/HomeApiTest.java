package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.port.out.DedupeRepository;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.orchestration.domain.Routine;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** T047 / T014 — página inicial (FR-016, US5-4, US1). */
class HomeApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;
  @Autowired DedupeRepository dedupe;
  @Autowired RoutineRepository routines;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_evidence").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_url_identity").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_external_identity").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_source").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_match_candidate").executeUpdate();
      em.createNativeQuery("DELETE FROM application").executeUpdate();
      em.createNativeQuery("DELETE FROM routine_missed").executeUpdate();
      em.createNativeQuery("UPDATE opportunity SET merged_into = NULL").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
      em.createNativeQuery("DELETE FROM integration_status").executeUpdate();
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
  void reflectsRealDataOnlyAndNeverInventsFreshness() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataAsOf").value(nullValue()))
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(1))
        .andExpect(jsonPath("$.assessment.state").value("pending"))
        .andExpect(jsonPath("$.assessment.stale").value(true))
        .andExpect(jsonPath("$.assessment.remainingCount").value(0))
        .andExpect(jsonPath("$.selectionToday.localDate").value("2026-11-14"))
        .andExpect(jsonPath("$.selectionToday.count").value(0))
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(0))
        .andExpect(jsonPath("$.selectionToday.limit").value(10))
        .andExpect(jsonPath("$.selectionToday.items").isEmpty())
        .andExpect(jsonPath("$.applicationsWithNextActions").isArray())
        .andExpect(jsonPath("$.mailNeedingAttention").isArray())
        .andExpect(jsonPath("$.reviewQueueCount").value(0))
        .andExpect(jsonPath("$.missedRoutines").isArray())
        .andExpect(jsonPath("$.pendingReviewItems").isArray())
        .andExpect(jsonPath("$.pendingReviewItems").isEmpty());

    ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            "ciandt-home-1",
            "https://jobs.lever.co/ciandt/ciandt-home-1",
            "Requisitos: Java e Spring Boot\nRemoto",
            "Desenvolvedor Java",
            "Remoto"),
        "CI&T");
    selections.selectToday();
    for (String url : List.of("https://one.test/job", "https://two.test/job"))
      mvc.perform(
              post("/api/opportunities/manual")
                  .session(s.session())
                  .header("X-CSRF-Token", s.csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"url\":\"" + url + "\",\"text\":\"Java remoto\"}"))
          .andExpect(status().isOk());

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(jsonPath("$.dataAsOf").value(notNullValue()))
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(1))
        .andExpect(jsonPath("$.assessment.remainingCount").value(0))
        .andExpect(jsonPath("$.selectionToday.count").value(1))
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(1))
        .andExpect(jsonPath("$.selectionToday.limit").value(10))
        .andExpect(jsonPath("$.selectionToday.items[0].title").value("Desenvolvedor Java"))
        .andExpect(jsonPath("$.selectionToday.items[0].fitReasons[0]").exists())
        .andExpect(jsonPath("$.selectionToday.items[0].compatibility.current").value(true))
        .andExpect(jsonPath("$.selectionToday.items[0].compatibility.roleStatus").value("matched"))
        .andExpect(
            jsonPath("$.selectionToday.items[0].compatibility.technologyStatus").value("matched"))
        .andExpect(jsonPath("$.reviewQueueCount").value(1))
        .andExpect(jsonPath("$.pendingReviewItems.length()").value(1))
        .andExpect(jsonPath("$.pendingReviewItems[0].kind").value("dedupe_candidate"))
        .andExpect(jsonPath("$.pendingReviewItems[0].kindLabel").value("DUPLICATA · revisão"))
        .andExpect(jsonPath("$.pendingReviewItems[0].targetRoute").value("/revisao"))
        .andExpect(jsonPath("$.integrations[?(@.id=='source:manual')].state").value(hasItem("ok")));
  }

  @Test
  void listsEachPendingItemWithSafeInternalRouteAndKeepsLegacyFields() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    String first = ingestJava("pending-1", "Desenvolvedor Java", "CI&T");
    String second = ingestJava("pending-2", "Desenvolvedor Java", "CI&T");
    String third = ingestJava("pending-3", "Engenheiro de Software", "Itaú");
    String fourth = ingestJava("pending-4", "Engenheiro de Software", "Itaú");
    writer.run(() -> {
      dedupe.suggest(first, second, clock.now());
      dedupe.suggest(third, fourth, clock.now().plusSeconds(1));
      routines.save(
          Routine.daily("home-routine", "search", null, LocalTime.of(7, 30), clock.now()));
      routines.recordMissed("home-routine", Instant.parse("2026-11-13T10:30:00Z"));
    });

    var body =
        mvc.perform(get("/api/home").session(s.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewQueueCount").value(2))
            .andExpect(jsonPath("$.mailNeedingAttention").isEmpty())
            .andExpect(jsonPath("$.missedRoutines.length()").value(1))
            .andExpect(jsonPath("$.missedRoutines[0].routineId").value("home-routine"))
            .andExpect(jsonPath("$.pendingReviewItems.length()").value(3))
            .andExpect(
                jsonPath(
                    "$.pendingReviewItems[*].kind",
                    contains("dedupe_candidate", "dedupe_candidate", "missed_routine")))
            .andExpect(jsonPath("$.pendingReviewItems[0].id").value("cand-" + pair(first, second)))
            .andExpect(jsonPath("$.pendingReviewItems[0].kindLabel").value("DUPLICATA · revisão"))
            .andExpect(
                jsonPath("$.pendingReviewItems[0].title")
                    .value("Possível vaga duplicada: Desenvolvedor Java · CI&T"))
            .andExpect(jsonPath("$.pendingReviewItems[0].summary", containsString("coincidentes")))
            .andExpect(jsonPath("$.pendingReviewItems[0].targetRoute").value("/revisao"))
            .andExpect(jsonPath("$.pendingReviewItems[2].kindLabel").value("ROTINA NÃO EXECUTADA"))
            .andExpect(jsonPath("$.pendingReviewItems[2].targetRoute").value("/atividades"))
            .andExpect(
                jsonPath("$.pendingReviewItems[2].summary", containsString("13/11 às 07:30")))
            .andReturn();
    String json = body.getResponse().getContentAsString();
    List<String> routes = JsonPath.read(json, "$.pendingReviewItems[*].targetRoute");
    assertThat(routes)
        .allSatisfy(route -> {
          assertThat(route).startsWith("/");
          assertThat(route).doesNotStartWith("//");
          assertThat(route).doesNotContain("\\");
        });
    // Paridade com needsYouCount do frontend: um item por pendência individual.
    int legacyTotal =
        (int) JsonPath.read(json, "$.reviewQueueCount")
            + ((List<?>) JsonPath.read(json, "$.mailNeedingAttention")).size()
            + ((List<?>) JsonPath.read(json, "$.missedRoutines")).size();
    assertThat(((List<?>) JsonPath.read(json, "$.pendingReviewItems"))).hasSize(legacyTotal);
  }

  @Test
  void dropsResolvedPendingItemsAndDoesNotMutateOnGet() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    String first = ingestJava("pending-5", "Desenvolvedor Java", "CI&T");
    String second = ingestJava("pending-6", "Desenvolvedor Java", "CI&T");
    writer.run(() -> dedupe.suggest(first, second, clock.now()));
    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(jsonPath("$.pendingReviewItems.length()").value(1));
    long activityBefore = jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single();

    mvc.perform(
            post("/api/dedupe-candidates/" + pair(first, second) + "/reject")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(jsonPath("$.reviewQueueCount").value(0))
        .andExpect(jsonPath("$.pendingReviewItems").isEmpty());
    assertThat(jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single())
        .isEqualTo(activityBefore + 1);
  }

  private static String pair(String a, String b) {
    return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
  }

  private String ingestJava(String externalId, String title, String company) {
    return ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            externalId,
            "https://jobs.lever.co/ciandt/" + externalId,
            "Requisitos: Java e Spring Boot\nRemoto",
            title,
            "Remoto"),
        company);
  }

  @Test
  void separatesVisibleCountFromHistoricalConsumedCountAndDoesNotMutateOnGet() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String firstId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "home-vis-1",
                "https://jobs.lever.co/ciandt/home-vis-1",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto"),
            "CI&T");
    String secondId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "home-vis-2",
                "https://jobs.lever.co/ciandt/home-vis-2",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto"),
            "CI&T");
    selections.selectToday();

    // Seed a real dataAsOf via manual input, keeping daily_selection untouched.
    mvc.perform(
            post("/api/opportunities/manual")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"url\":\"https://example.com/jobs/manual-home\",\"text\":\"Java remoto\"}"))
        .andExpect(status().isOk());

    var initialHome =
        mvc.perform(get("/api/home").session(s.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selectionToday.count").value(2))
            .andExpect(jsonPath("$.selectionToday.consumedCount").value(2))
            .andExpect(jsonPath("$.selectionToday.limit").value(10))
            .andExpect(
                jsonPath("$.selectionToday.items[*].id", containsInAnyOrder(firstId, secondId)))
            .andReturn();
    String dataAsOfBefore =
        JsonPath.read(initialHome.getResponse().getContentAsString(), "$.dataAsOf");

    // Discard one selected item: visible count drops to 1, consumedCount remains 2.
    mvc.perform(
            post("/api/opportunities/" + firstId + "/discard")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectionToday.count").value(1))
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(2))
        .andExpect(jsonPath("$.selectionToday.limit").value(10))
        .andExpect(jsonPath("$.selectionToday.items[*].id", contains(secondId)));

    // Advance clock and update criteria: visible becomes 0, consumedCount stays 2, dataAsOf
    // unchanged.
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), AppClock.ZONE));
    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"targetRoles":["Backend Developer"],"stackKeywords":["Python"],
                     "remotePreferred":true,"hybridRegions":[],"cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,
                     "acceptJuniorIfJustified":true,"dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,
                     "urgentHours":48,"emailDigestEnabled":false}
                    """))
        .andExpect(status().isOk());

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataAsOf").value(dataAsOfBefore))
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(2))
        .andExpect(jsonPath("$.assessment.state").value("pending"))
        .andExpect(jsonPath("$.assessment.stale").value(true))
        .andExpect(jsonPath("$.assessment.remainingCount").value(3))
        .andExpect(jsonPath("$.selectionToday.count").value(0))
        .andExpect(jsonPath("$.selectionToday.consumedCount").value(2))
        .andExpect(jsonPath("$.selectionToday.limit").value(10))
        .andExpect(jsonPath("$.selectionToday.items").isEmpty());

    // Advance clock again and call GET /api/home repeatedly: no timestamps or tables mutate.
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), AppClock.ZONE));
    long activityBefore = jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single();
    long tasksBefore = jdbc.sql("SELECT count(*) FROM task_run").query(Long.class).single();
    long selectedBefore =
        jdbc.sql("SELECT count(*) FROM daily_selection").query(Long.class).single();

    for (int i = 0; i < 2; i++) {
      mvc.perform(get("/api/home").session(s.session()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dataAsOf").value(dataAsOfBefore))
          .andExpect(jsonPath("$.assessment.criteriaVersion").value(2))
          .andExpect(jsonPath("$.assessment.state").value("pending"))
          .andExpect(jsonPath("$.assessment.startedAt").value(nullValue()))
          .andExpect(jsonPath("$.assessment.finishedAt").value(nullValue()))
          .andExpect(jsonPath("$.selectionToday.count").value(0))
          .andExpect(jsonPath("$.selectionToday.consumedCount").value(2));
    }

    assertThat(jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single())
        .isEqualTo(activityBefore);
    assertThat(jdbc.sql("SELECT count(*) FROM task_run").query(Long.class).single())
        .isEqualTo(tasksBefore);
    assertThat(jdbc.sql("SELECT count(*) FROM daily_selection").query(Long.class).single())
        .isEqualTo(selectedBefore);
  }
}
