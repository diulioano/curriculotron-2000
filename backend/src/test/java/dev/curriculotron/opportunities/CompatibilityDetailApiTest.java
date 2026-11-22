package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CompatibilityDetailApiTest extends AppTestBase {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), AppClock.ZONE));
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
  void returnsFullCompatibilityEvidenceCriteriaSnapshotAndNoResumeData() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "detail-java-17",
                "https://jobs.lever.co/ciandt/detail-java-17",
                """
                Sobre a equipe: atuamos em plataformas financeiras remotas.
                Requisitos:
                Experiência sólida com Java 17, Spring Boot e APIs REST.
                Diferenciais:
                Desejável conhecimento em Angular ou Kafka.
                Observação: não usamos PHP nesta frente.
                """,
                "Desenvolvedor Backend Pleno Java",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");

    String body =
        mvc.perform(get("/api/opportunities/" + id).session(s.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.compatibility.current").value(true))
            .andExpect(jsonPath("$.compatibility.criteriaVersion").value(1))
            .andExpect(jsonPath("$.compatibility.evaluatorVersion").value(2))
            .andExpect(jsonPath("$.compatibility.roleStatus").value("matched"))
            .andExpect(jsonPath("$.compatibility.technologyStatus").value("matched"))
            .andExpect(
                jsonPath("$.compatibilityCriteria.targetRoles")
                    .value(hasItems("Desenvolvedor Backend Pleno Java", "Software Engineer Java")))
            .andExpect(
                jsonPath("$.compatibilityCriteria.stackKeywords")
                    .value(hasItems("Java 17", "Spring Boot", "Angular")))
            .andExpect(jsonPath("$.compatibilityEvidence").isNotEmpty())
            .andExpect(
                jsonPath("$.compatibilityEvidence[*].field", hasItems("title", "description")))
            .andExpect(
                jsonPath(
                    "$.compatibilityEvidence[*].mentionKind", hasItems("required", "optional")))
            .andExpect(jsonPath("$.compatibilityEvidence[*].source", hasItem("lever:ciandt")))
            .andExpect(
                jsonPath(
                    "$.compatibilityEvidence[*].sourceUrl",
                    hasItem("https://jobs.lever.co/ciandt/detail-java-17")))
            .andExpect(jsonPath("$.facts").isNotEmpty())
            .andExpect(jsonPath("$.inferred").isArray())
            .andExpect(jsonPath("$.salaryReference.status").exists())
            .andExpect(
                jsonPath("$.sources[0].url").value("https://jobs.lever.co/ciandt/detail-java-17"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode tree = JSON.readTree(body);
    assertNoResumeFields(tree);
    assertThat(tree.get("compatibilityEvidence"))
        .anySatisfy(item -> {
          assertThat(item.get("matchedCriterion").asString()).isEqualTo("Java 17");
          assertThat(item.get("observedTerm").asString()).containsIgnoringCase("Java");
          assertThat(item.get("observedVersion").asString()).isEqualTo("17");
          assertThat(item.get("configuredVersion").asString()).isEqualTo("17");
          assertThat(item.get("section").asString()).isNotBlank();
          assertThat(item.get("fetchedAt").asString()).isEqualTo("2026-11-15T12:00:00Z");
          assertThat(item.get("quote").asString()).contains("Java 17");
        });
  }

  @Test
  void includesMissingCompatibilityAndDescriptionContextForPartialAndUnknownItems()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String id =
        ingestion.ingest(
            new JobSource.Item(
                "gupy:db",
                "partial-missing-detail",
                "https://db.gupy.io/job/partial-missing-detail",
                "Resumo curto de listagem sem requisitos técnicos.",
                "Especialista de Plataforma",
                "",
                clock.localDate(),
                Completeness.partial,
                "listing_summary"),
            "Digital Business");

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(true))
        .andExpect(jsonPath("$.compatibility.roleStatus").value("unknown"))
        .andExpect(jsonPath("$.compatibility.technologyStatus").value("unknown"))
        .andExpect(
            jsonPath("$.missing")
                .value(
                    hasItems(
                        "Salário a confirmar",
                        "Descrição completa da vaga a confirmar (conteúdo parcial ou resumido)",
                        "Cargo compatível não confirmado na vaga",
                        "Tecnologias exigidas não confirmadas na vaga")));
  }

  @Test
  void marksStaleSnapshotCurrentFalseAndHidesContradictoryFitReasonsWithoutMutatingDb()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "stale-detail-1",
                "https://jobs.lever.co/ciandt/stale-detail-1",
                "Requisitos: Java 17 e Spring Boot.",
                "Desenvolvedor Backend Pleno Java",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");

    writer.run(() ->
        em.createNativeQuery(
                """
                UPDATE search_criteria
                SET version = 2,
                    target_roles = '["Desenvolvedor Backend Python"]',
                    stack_keywords = '["Python","Django"]'
                WHERE id = 1
                """)
            .executeUpdate());

    long compatBefore =
        jdbc.sql("SELECT COUNT(*) FROM opportunity_compatibility").query(Long.class).single();
    long evidenceBefore =
        jdbc.sql("SELECT COUNT(*) FROM opportunity_evidence").query(Long.class).single();

    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(false))
        .andExpect(jsonPath("$.compatibility.criteriaVersion").value(1))
        .andExpect(jsonPath("$.compatibility.roleStatus").value("matched"))
        .andExpect(jsonPath("$.fitReasons").isEmpty())
        .andExpect(
            jsonPath("$.compatibilityCriteria.targetRoles")
                .value(hasItem("Desenvolvedor Backend Pleno Java")))
        .andExpect(jsonPath("$.compatibilityCriteria.stackKeywords").value(hasItem("Java 17")))
        .andExpect(jsonPath("$.compatibilityEvidence").isNotEmpty());

    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM opportunity_compatibility").query(Long.class).single())
        .isEqualTo(compatBefore);
    assertThat(jdbc.sql("SELECT COUNT(*) FROM opportunity_evidence").query(Long.class).single())
        .isEqualTo(evidenceBefore);
  }

  @Test
  void returnsEmptyEvidenceNullCriteriaAndMissingNoticeForUnassessedOpportunity() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    writer.run(() ->
        em.createNativeQuery(
                """
                INSERT INTO opportunity(
                  id, title, company_name, location, work_mode, contract_type,
                  requirements_facts, requirements_inferred, availability,
                  fit, fit_score, fit_reasons, user_decision, evidence_revision, created_at, updated_at
                ) VALUES (
                  'legacy-unassessed-1', 'Engenheiro Legado', 'Empresa X', 'Remoto', 'remote', 'clt',
                  '["Java"]', '[]', 'open',
                  'strong', 80, '["Java legado no perfil"]', 'none', 1, '2026-11-14T10:00:00Z', '2026-11-14T10:00:00Z'
                )
                """)
            .executeUpdate());

    mvc.perform(get("/api/opportunities/legacy-unassessed-1").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(false))
        .andExpect(jsonPath("$.compatibility.criteriaVersion").value(nullValue()))
        .andExpect(jsonPath("$.compatibility.reasonCodes[0]").value("not_assessed"))
        .andExpect(jsonPath("$.compatibilityCriteria").value(nullValue()))
        .andExpect(jsonPath("$.compatibilityEvidence").isEmpty())
        .andExpect(jsonPath("$.fitReasons").isEmpty())
        .andExpect(jsonPath("$.facts[0]").value("Java"))
        .andExpect(
            jsonPath("$.missing")
                .value(
                    hasItems(
                        "Salário a confirmar",
                        "Descrição completa da vaga a confirmar",
                        "Avaliação de compatibilidade ainda não executada")));
  }

  private static void assertNoResumeFields(JsonNode node) {
    Set<String> forbidden =
        Set.of("resume", "cv", "curriculo", "currículo", "curriculum", "experience");
    node.propertyNames()
        .forEach(name -> {
          assertThat(forbidden).doesNotContain(name.toLowerCase(Locale.ROOT));
          JsonNode child = node.get(name);
          if (child.isObject()) assertNoResumeFields(child);
          if (child.isArray()) {
            for (JsonNode el : child) {
              if (el.isObject()) assertNoResumeFields(el);
            }
          }
        });
  }
}
