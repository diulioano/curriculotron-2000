package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.*;
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

class OpportunityApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired SelectionRepository selectionRepository;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;

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
      em.createNativeQuery("UPDATE opportunity SET merged_into = NULL").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
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
  void manualReviewDiscardAndRestore() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    var response =
        mvc.perform(
                post("/api/opportunities/manual")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/jobs/123\",\"text\":\"Java remoto\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String id = JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").isEmpty())
        .andExpect(jsonPath("$.facts[0]").value("Java remoto"))
        .andExpect(jsonPath("$.inferred").isEmpty())
        .andExpect(jsonPath("$.sources[0].url").value("https://example.com/jobs/123"))
        .andExpect(jsonPath("$.sources[0].fetchedAt").exists())
        .andExpect(
            jsonPath("$.missing")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "Salário a confirmar",
                        "Tipo de contrato a confirmar",
                        "Localização a confirmar")));
    mvc.perform(get("/api/opportunities?list=unknown").session(s.session()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_list"));
    mvc.perform(get("/api/opportunities?date=ontem").session(s.session()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_parameter"));
    mvc.perform(get("/api/opportunities/nao-existe").session(s.session()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/opportunities/manual")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"ftp://example.com/x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_input"));
    mvc.perform(
            post("/api/opportunities/" + id + "/discard")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
        .andExpect(jsonPath("$.items[0].id").value(id));
    mvc.perform(
            post("/api/opportunities/" + id + "/restore")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(jsonPath("$.items[0].id").value(id));
  }

  @Test
  void
      fifteenCompatibleSplitAcrossSelectionAndSecondaryWhileExcludingIncompatibleAndLabelingUnknown()
          throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    for (int i = 1; i <= 15; i++) {
      ingestion.ingest(
          new JobSource.Item(
              "lever:ciandt",
              "compat-" + i,
              "https://jobs.lever.co/ciandt/compat-" + i,
              "Requisitos: Java e Spring Boot\nTrabalho remoto",
              "Desenvolvedor Backend Pleno",
              "Remoto",
              clock.localDate(),
              Completeness.complete,
              null),
          "CI&T");
    }
    String partialUnknownId =
        ingestion.ingest(
            new JobSource.Item(
                "gupy:db",
                "partial-1",
                "https://db.gupy.io/job/partial-1",
                "Atuação em sistemas corporativos.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.partial,
                "listing_summary"),
            "Digital Business");
    String unknownRoleId =
        ingestion.ingest(
            new JobSource.Item(
                "gupy:db",
                "unknown-role-1",
                "https://db.gupy.io/job/unknown-role-1",
                "Requisitos: Java e Spring Boot.",
                "Especialista de Plataforma",
                "Remoto",
                clock.localDate(),
                Completeness.partial,
                "listing_summary"),
            "Digital Business");
    String mismatchedRoleId =
        ingestion.ingest(
            new JobSource.Item(
                "cwi:oportunidades",
                "design-1",
                "https://cwi.com.br/talentos/oportunidade/service-designer-1",
                "Responsabilidades: mapear jornadas.\nOutra equipe utiliza Java.",
                "Service Designer",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CWI");
    String mismatchedTechId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "salesforce-1",
                "https://jobs.lever.co/ciandt/salesforce-1",
                "Requisitos: Salesforce Marketing Cloud exclusivamente.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");

    selections.selectToday();

    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(10))
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(1))
        .andExpect(jsonPath("$.assessment.remainingCount").value(0))
        .andExpect(jsonPath("$.items[0].compatibility.criteriaVersion").value(1))
        .andExpect(jsonPath("$.items[0].compatibility.evaluatorVersion").value(1))
        .andExpect(jsonPath("$.items[0].compatibility.assessedAt").isString())
        .andExpect(jsonPath("$.items[0].compatibility.current").value(true))
        .andExpect(jsonPath("$.items[0].compatibility.roleStatus").value("matched"))
        .andExpect(jsonPath("$.items[0].compatibility.technologyStatus").value("matched"))
        .andExpect(jsonPath("$.items[0].compatibility.reasonCodes").isNotEmpty())
        .andExpect(jsonPath("$.items[*].id", not(hasItem(partialUnknownId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(unknownRoleId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(mismatchedRoleId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(mismatchedTechId))));

    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(7))
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(1))
        .andExpect(jsonPath("$.items[*].id", hasItem(partialUnknownId)))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + partialUnknownId + "')].compatibility.current",
                hasItem(true)))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + partialUnknownId + "')].compatibility.roleStatus",
                hasItem("matched")))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + partialUnknownId + "')].compatibility.technologyStatus",
                hasItem("unknown")))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + partialUnknownId + "')].fitReasons[*]",
                hasItem("Compatibilidade tecnológica não confirmada.")))
        .andExpect(jsonPath("$.items[*].id", hasItem(unknownRoleId)))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + unknownRoleId + "')].compatibility.roleStatus",
                hasItem("unknown")))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + unknownRoleId + "')].compatibility.technologyStatus",
                hasItem("matched")))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + unknownRoleId + "')].fitReasons[*]",
                hasItem("Função compatível ainda não confirmada.")))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(mismatchedRoleId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(mismatchedTechId))));
  }

  @Test
  void zeroCompatibleHidesIncompatibleEvenFromPriorityCompanyAndPreservesHistoryOnCriteriaChange()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    ingestion.ingest(
        new JobSource.Item(
            "cwi:oportunidades",
            "pm-1",
            "https://cwi.com.br/talentos/oportunidade/data-pm-1",
            "Requisitos: gestão de produto e roadmap.",
            "Data Product Manager",
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        "CWI");
    ingestion.ingest(
        new JobSource.Item(
            "lever:bv",
            "sf-1",
            "https://jobs.lever.co/bv/sf-1",
            "Requisitos: Salesforce Marketing Cloud.",
            "Desenvolvedor Backend",
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        "BV");
    String discardedId =
        ingestion.ingest(
            new JobSource.Item(
                "cwi:oportunidades",
                "discarded-1",
                "https://cwi.com.br/talentos/oportunidade/discarded-1",
                "Requisitos: Salesforce apenas.",
                "Desenvolvedor Backend",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CWI");
    mvc.perform(
            post("/api/opportunities/" + discardedId + "/discard")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    selections.selectToday();

    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].id", hasItem(discardedId)));

    // Compatible selection becomes hidden after criteria version increments without deleting
    // daily_selection history.
    String compatibleId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "java-ok",
                "https://jobs.lever.co/ciandt/java-ok",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    selections.selectToday();
    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].id", hasItem(compatibleId)));

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

    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(2))
        .andExpect(jsonPath("$.assessment.state").value("pending"))
        .andExpect(jsonPath("$.assessment.stale").value(true));
    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(compatibleId);
  }

  @Test
  void opportunityListEnvelopeShowsStaleAndLegacyCompatibilityWithoutGetSideEffects()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    // Record a real dataAsOf via manual input, then ingest a compatible job and a discarded job.
    mvc.perform(
            post("/api/opportunities/manual")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/jobs/seed\",\"text\":\"Java remoto\"}"))
        .andExpect(status().isOk());

    String selectedId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "env-ok",
                "https://jobs.lever.co/ciandt/env-ok",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    selections.selectToday();

    String discardedAssessedId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "env-disc",
                "https://jobs.lever.co/ciandt/env-disc",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    mvc.perform(
            post("/api/opportunities/" + discardedAssessedId + "/discard")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    String legacyDiscardedId = "legacy-disc-1";
    writer.run(() ->
        em.createNativeQuery(
                """
                INSERT INTO opportunity (
                  id, title, company_name, location, work_mode, contract_type, salary_unit,
                  requirements_facts, requirements_inferred, availability, fit, fit_score,
                  fit_reasons, user_decision, created_at, updated_at
                ) VALUES (
                  :id, 'Vaga Legada', 'Empresa X', 'Remoto', 'remote', 'clt', 'monthly',
                  '["Java"]', '[]', 'open', 'weak', 10,
                  '["Java no perfil"]', 'discarded', '2026-11-10T10:00:00Z', '2026-11-10T10:00:00Z'
                )
                """)
            .setParameter("id", legacyDiscardedId)
            .executeUpdate());

    // Other ApiListResponse endpoints must remain untouched (no assessment key).
    mvc.perform(get("/api/task-runs").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.assessment").doesNotExist());
    mvc.perform(get("/api/activity").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.assessment").doesNotExist());

    var selectionResp =
        mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", contains(selectedId)))
            .andExpect(jsonPath("$.items[0].compatibility.current").value(true))
            .andExpect(jsonPath("$.dataAsOf").isString())
            .andExpect(jsonPath("$.assessment.criteriaVersion").value(1))
            .andReturn();
    String dataAsOfBefore =
        JsonPath.read(selectionResp.getResponse().getContentAsString(), "$.dataAsOf");

    mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.current",
                contains(false)))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.criteriaVersion",
                contains(nullValue())))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.evaluatorVersion",
                contains(nullValue())))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.assessedAt",
                contains(nullValue())))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.roleStatus",
                contains("unknown")))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.technologyStatus",
                contains("unknown")))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + legacyDiscardedId + "')].compatibility.reasonCodes[0]",
                contains("not_assessed")))
        .andExpect(jsonPath("$.items[?(@.id=='" + legacyDiscardedId + "')].fitReasons[*]", empty()))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + discardedAssessedId + "')].compatibility.current",
                contains(true)));

    // Advance clock and update criteria -> dataAsOf stays unchanged, discarded assessed becomes
    // current=false and suppresses stale fitReasons.
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

    var discardedAfterPut =
        mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataAsOf").value(dataAsOfBefore))
            .andExpect(jsonPath("$.assessment.criteriaVersion").value(2))
            .andExpect(jsonPath("$.assessment.state").value("pending"))
            .andExpect(jsonPath("$.assessment.stale").value(true))
            .andExpect(jsonPath("$.assessment.remainingCount").value(4))
            .andExpect(
                jsonPath(
                    "$.items[?(@.id=='" + discardedAssessedId + "')].compatibility.current",
                    contains(false)))
            .andExpect(
                jsonPath(
                    "$.items[?(@.id=='" + discardedAssessedId + "')].compatibility.criteriaVersion",
                    contains(1)))
            .andExpect(
                jsonPath("$.items[?(@.id=='" + discardedAssessedId + "')].fitReasons[*]", empty()))
            .andReturn();
    List<String> assessedAtList =
        JsonPath.read(
            discardedAfterPut.getResponse().getContentAsString(),
            "$.items[?(@.id=='" + discardedAssessedId + "')].compatibility.assessedAt");

    // Advance clock again and execute repeated GETs: zero mutations and identical timestamps.
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), AppClock.ZONE));
    long activityBefore = jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single();
    long tasksBefore = jdbc.sql("SELECT count(*) FROM task_run").query(Long.class).single();
    long compatibilityBefore =
        jdbc.sql("SELECT count(*) FROM opportunity_compatibility").query(Long.class).single();

    for (String listName : List.of("selection", "secondary", "discarded")) {
      mvc.perform(get("/api/opportunities?list=" + listName).session(s.session()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dataAsOf").value(dataAsOfBefore))
          .andExpect(jsonPath("$.assessment.criteriaVersion").value(2))
          .andExpect(jsonPath("$.assessment.state").value("pending"))
          .andExpect(jsonPath("$.assessment.startedAt").value(nullValue()))
          .andExpect(jsonPath("$.assessment.finishedAt").value(nullValue()))
          .andExpect(jsonPath("$.assessment.remainingCount").value(4));
    }

    mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + discardedAssessedId + "')].compatibility.assessedAt",
                contains(assessedAtList.getFirst())));

    assertThat(jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single())
        .isEqualTo(activityBefore);
    assertThat(jdbc.sql("SELECT count(*) FROM task_run").query(Long.class).single())
        .isEqualTo(tasksBefore);
    assertThat(
            jdbc.sql("SELECT count(*) FROM opportunity_compatibility").query(Long.class).single())
        .isEqualTo(compatibilityBefore);
  }

  private String ingestWith(String externalId, String title, String text) {
    return ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            externalId,
            "https://jobs.lever.co/ciandt/" + externalId,
            text,
            title,
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        "CI&T");
  }

  @Test
  void detailExposesSeniorityAsFactInferredOrAbsent() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    String fact =
        ingestWith("seniority-fact", "Desenvolvedor Backend Pleno", "Requisitos: Java e Spring.");
    String inferred =
        ingestWith(
            "seniority-inferred",
            "Desenvolvedor Backend Java",
            "Requisitos: Java e Spring Boot. Exigimos 3+ anos de experiência.");
    String absent =
        ingestWith(
            "seniority-absent", "Desenvolvedor Backend Java", "Requisitos: Java e Spring Boot.");

    mvc.perform(get("/api/opportunities/" + fact).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority.status").value("fact"))
        .andExpect(jsonPath("$.seniority.label").value("Pleno"))
        .andExpect(jsonPath("$.seniority.reason").value(nullValue()));
    mvc.perform(get("/api/opportunities/" + inferred).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority.status").value("inferred"))
        .andExpect(jsonPath("$.seniority.label").value("Pleno"))
        .andExpect(jsonPath("$.seniority.reason").value("inferido: “3+ anos”"));
    mvc.perform(get("/api/opportunities/" + absent).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seniority.status").value("absent"))
        .andExpect(jsonPath("$.seniority.label").value(nullValue()))
        .andExpect(jsonPath("$.seniority.reason").value(nullValue()));
  }

  @Test
  void externalVisitRecordsInformationalEventWithoutDecisionOrApplication() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    String id = ingestWith("visit-1", "Desenvolvedor Backend Pleno", "Requisitos: Java.");
    var before = jdbc.sql("SELECT * FROM opportunity WHERE id = ?").param(id).query().singleRow();
    long opened = externalOpened(id);

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeline[?(@.kind=='external_opened')]").isEmpty());
    mvc.perform(get("/api/opportunities/" + id).session(s.session())).andExpect(status().isOk());
    assertThat(externalOpened(id)).isEqualTo(opened);

    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), AppClock.ZONE));
    mvc.perform(
            post("/api/opportunities/" + id + "/external-visit")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(externalOpened(id)).isEqualTo(opened + 1);
    assertThat(
            jdbc.sql(
                    "SELECT author FROM activity_log WHERE action = 'opportunity.external_opened'"
                        + " AND target_type = 'opportunity' AND target_id = ?")
                .param(id)
                .query(String.class)
                .single())
        .isEqualTo("user");
    assertThat(jdbc.sql("SELECT * FROM opportunity WHERE id = ?").param(id).query().singleRow())
        .isEqualTo(before);
    assertThat(
            jdbc.sql("SELECT user_decision FROM opportunity WHERE id = ?")
                .param(id)
                .query(String.class)
                .single())
        .isEqualTo("none");
    assertThat(
            jdbc.sql("SELECT count(*) FROM application WHERE opportunity_id = ?")
                .param(id)
                .query(Long.class)
                .single())
        .isZero();

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userDecision").value("none"))
        .andExpect(jsonPath("$.timeline[?(@.kind=='external_opened')].actorLabel").value("você"))
        .andExpect(
            jsonPath("$.timeline[?(@.kind=='external_opened')].description")
                .value("Abriu o link da vaga."))
        .andExpect(
            jsonPath("$.timeline[?(@.kind=='external_opened')].note")
                .value("isso não registra candidatura"))
        .andExpect(
            jsonPath("$.timeline[?(@.kind=='external_opened')].at").value("2026-11-14T16:00:00Z"))
        .andExpect(jsonPath("$.timeline[?(@.kind=='applied')]").isEmpty());
    assertThat(externalOpened(id)).isEqualTo(opened + 1);
  }

  @Test
  void externalVisitRequiresSessionCsrfAndExistingOpportunity() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    String id = ingestWith("visit-2", "Desenvolvedor Backend Pleno", "Requisitos: Java.");
    String path = "/api/opportunities/" + id + "/external-visit";
    long opened = externalOpened(id);

    mvc.perform(post(path)).andExpect(status().isForbidden());
    mvc.perform(post(path).session(s.session())).andExpect(status().isForbidden());
    var anonymous = TestSession.anonymous(mvc);
    mvc.perform(post(path).session(anonymous.session()).header("X-CSRF-Token", anonymous.csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(path).session(s.session())).andExpect(status().is4xxClientError());
    assertThat(externalOpened(id)).isEqualTo(opened);

    long activityBefore = jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single();
    mvc.perform(
            post("/api/opportunities/nao-existe/external-visit")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"));
    assertThat(jdbc.sql("SELECT count(*) FROM activity_log").query(Long.class).single())
        .isEqualTo(activityBefore);
  }

  @Test
  void timelineOrdersDiscoveryAndDecisionsChronologicallyAndMapsRestoreActionNone()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), AppClock.ZONE));
    String id = ingestWith("timeline-basic", "Desenvolvedor Backend Pleno", "Requisitos: Java.");
    for (var step :
        List.of(
            new String[] {"2026-11-14T11:00:00Z", "discard"},
            new String[] {"2026-11-14T12:00:00Z", "restore"})) {
      clock.setClock(Clock.fixed(Instant.parse(step[0]), AppClock.ZONE));
      mvc.perform(
              post("/api/opportunities/" + id + "/" + step[1])
                  .session(s.session())
                  .header("X-CSRF-Token", s.csrf()))
          .andExpect(status().isOk());
    }

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeline[*].kind", contains("discovered", "discarded", "restored")))
        .andExpect(
            jsonPath(
                "$.timeline[*].at",
                contains("2026-11-14T10:00:00Z", "2026-11-14T11:00:00Z", "2026-11-14T12:00:00Z")))
        .andExpect(jsonPath("$.timeline[0].actorLabel").value("bot Busca de vagas"))
        .andExpect(jsonPath("$.timeline[0].description").value("Encontrada em lever:ciandt."))
        .andExpect(jsonPath("$.timeline[0].note").value(nullValue()))
        .andExpect(jsonPath("$.timeline[1].actorLabel").value("você"))
        .andExpect(jsonPath("$.timeline[1].description").value("Descartou a vaga."))
        .andExpect(jsonPath("$.timeline[2].actorLabel").value("você"))
        .andExpect(jsonPath("$.timeline[2].description").value("Restaurou a vaga."))
        .andExpect(jsonPath("$.timeline[2].note").value(nullValue()));
  }

  @Test
  void manualEntryIsLabeledAsManualDiscovery() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    var response =
        mvc.perform(
                post("/api/opportunities/manual")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/jobs/timeline\",\"text\":\"Java\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String id = JsonPath.read(response.getResponse().getContentAsString(), "$.id");

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(jsonPath("$.timeline[0].kind").value("discovered"))
        .andExpect(jsonPath("$.timeline[0].actorLabel").value("entrada manual"))
        .andExpect(jsonPath("$.timeline[0].description").value("Encontrada em manual."));
  }

  private long externalOpened(String id) {
    return jdbc.sql(
            "SELECT count(*) FROM activity_log WHERE action = 'opportunity.external_opened'"
                + " AND target_id = ?")
        .param(id)
        .query(Long.class)
        .single();
  }
}
