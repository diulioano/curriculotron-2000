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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class OpportunityApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired SelectionRepository selectionRepository;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
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
        .andExpect(jsonPath("$.items[*].id", not(hasItem(partialUnknownId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(unknownRoleId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(mismatchedRoleId))))
        .andExpect(jsonPath("$.items[*].id", not(hasItem(mismatchedTechId))));

    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(7))
        .andExpect(jsonPath("$.items[*].id", hasItem(partialUnknownId)))
        .andExpect(
            jsonPath(
                "$.items[?(@.id=='" + partialUnknownId + "')].fitReasons[*]",
                hasItem("Compatibilidade tecnológica não confirmada.")))
        .andExpect(jsonPath("$.items[*].id", hasItem(unknownRoleId)))
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
        .andExpect(jsonPath("$.items").isEmpty());
    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(compatibleId);
  }
}
