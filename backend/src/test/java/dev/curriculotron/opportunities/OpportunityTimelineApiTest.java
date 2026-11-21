package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
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
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Linha do tempo completa da vaga contra SQLite real: descoberta, seleção, abertura do link,
 * correção agrupada, descarte, restauração e candidatura, sem duplicar eventos. Corrigir uma vaga
 * grava linhas append-only, por isso este cenário fica em classe própria (contexto descartável).
 */
class OpportunityTimelineApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired SelectionRepository selectionRepository;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;

  @BeforeEach
  void configureCriteria() {
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
    });
  }

  private void at(String iso) {
    clock.setClock(Clock.fixed(Instant.parse(iso), AppClock.ZONE));
  }

  private ResultActions act(TestSession s, String path, String body) throws Exception {
    var request =
        post(path)
            .session(s.session())
            .header("X-CSRF-Token", s.csrf())
            .contentType(MediaType.APPLICATION_JSON);
    return mvc.perform(body == null ? request : request.content(body));
  }

  @Test
  void combinesAllEventSourcesChronologicallyWithoutDuplicates() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    at("2026-11-13T15:00:00Z");
    String id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "timeline-full",
                "https://jobs.lever.co/ciandt/timeline-full",
                "Requisitos: Java e Spring Boot\nTrabalho remoto",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                LocalDate.of(2026, 11, 13),
                Completeness.complete,
                null),
            "CI&T");
    at("2026-11-14T13:00:00Z");
    selections.selectToday();
    assertThat(selectionRepository.selectedDays(id)).containsExactly(LocalDate.of(2026, 11, 14));

    at("2026-11-14T15:00:00Z");
    act(s, "/api/opportunities/" + id + "/external-visit", null).andExpect(status().isNoContent());

    at("2026-11-14T16:00:00Z");
    String correction =
        """
        {"title":"Desenvolvedor Backend Pleno Java","location":"São Leopoldo, RS",
         "reason":"Confirmado no edital"}
        """;
    act(s, "/api/opportunities/" + id + "/corrections", correction)
        .andExpect(status().isOk())
        // Uma única operação com dois campos vira um único evento (sem duplicar com activity_log).
        .andExpect(jsonPath("$.timeline[?(@.kind=='corrected')]", hasSize(1)))
        .andExpect(jsonPath("$.corrections", hasSize(2)))
        .andExpect(jsonPath("$.seniority.status").value("fact"));

    at("2026-11-14T16:30:00Z");
    act(s, "/api/opportunities/" + id + "/discard", null).andExpect(status().isOk());
    at("2026-11-14T17:00:00Z");
    act(s, "/api/opportunities/" + id + "/restore", null).andExpect(status().isOk());
    at("2026-11-14T18:00:00Z");
    act(s, "/api/opportunities/" + id + "/applied", null).andExpect(status().isOk());

    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM activity_log WHERE action = 'opportunity.corrected'"
                        + " AND target_id = ?")
                .param(id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT count(*) FROM activity_log WHERE action = 'application.created'")
                .query(Long.class)
                .single())
        .isEqualTo(1);

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.timeline[*].kind",
                contains(
                    "discovered",
                    "selected_today",
                    "external_opened",
                    "corrected",
                    "discarded",
                    "restored",
                    "applied")))
        .andExpect(
            jsonPath(
                "$.timeline[*].at",
                contains(
                    "2026-11-13T15:00:00Z",
                    "2026-11-14T03:00:00Z",
                    "2026-11-14T15:00:00Z",
                    "2026-11-14T16:00:00Z",
                    "2026-11-14T16:30:00Z",
                    "2026-11-14T17:00:00Z",
                    "2026-11-14T18:00:00Z")))
        .andExpect(
            jsonPath(
                "$.timeline[*].actorLabel",
                contains("bot Busca de vagas", "sistema", "você", "você", "você", "você", "você")))
        .andExpect(
            jsonPath("$.timeline[1].description")
                .value("Selecionada para a revisão do dia 14/11/2026."))
        .andExpect(jsonPath("$.timeline[2].note").value("isso não registra candidatura"))
        .andExpect(jsonPath("$.timeline[3].description").value("Corrigiu título e local."))
        .andExpect(jsonPath("$.timeline[3].note").value("Confirmado no edital"))
        .andExpect(jsonPath("$.timeline[6].description").value("Registrou candidatura."))
        .andExpect(jsonPath("$.timeline[6].note").value(nullValue()))
        .andExpect(jsonPath("$.userDecision").value("applied"));
  }

  @Test
  void separateCorrectionOperationsProduceSeparateEvents() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    at("2026-11-14T10:00:00Z");
    String id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "timeline-corrections",
                "https://jobs.lever.co/ciandt/timeline-corrections",
                "Requisitos: Java e Spring Boot",
                "Desenvolvedor Backend Java",
                "Remoto",
                LocalDate.of(2026, 11, 14),
                Completeness.complete,
                null),
            "CI&T");

    at("2026-11-14T11:00:00Z");
    act(s, "/api/opportunities/" + id + "/corrections", "{\"availability\":\"closed\"}")
        .andExpect(status().isOk());
    at("2026-11-14T12:00:00Z");
    act(
            s,
            "/api/opportunities/" + id + "/corrections",
            "{\"salary\":{\"min\":9000,\"max\":12000,\"unit\":\"monthly\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeline[*].kind", contains("discovered", "corrected", "corrected")))
        .andExpect(jsonPath("$.timeline[1].description").value("Corrigiu disponibilidade."))
        .andExpect(jsonPath("$.timeline[1].note").value(nullValue()))
        .andExpect(
            jsonPath("$.timeline[2].description")
                .value("Corrigiu salário mínimo, salário máximo e unidade do salário."));
  }
}
