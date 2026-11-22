package dev.curriculotron.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.opportunities.application.AssessOpportunityService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

class ProfileApiTest extends AppTestBase {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String POSTING =
      """
      Requisitos:
      Experiência com Java e Spring Boot.
      Diferenciais:
      Desejável Docker.
      Responsabilidades: você vai evoluir integrações com Kafka.
      Sobre a empresa: outras equipes usam Python.
      """;

  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired AssessOpportunityService assessor;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired JdbcClient jdbc;
  @Autowired AppClock clock;

  private TestSession session;

  @BeforeEach
  void reset() throws Exception {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      for (String table :
          new String[] {
            "daily_selection",
            "opportunity_compatibility",
            "opportunity_evidence",
            "opportunity_url_identity",
            "opportunity_external_identity",
            "opportunity_source",
            "opportunity_match_candidate",
            "application",
            "career_profile"
          }) em.createNativeQuery("DELETE FROM " + table).executeUpdate();
      em.createNativeQuery("UPDATE opportunity SET merged_into = NULL").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
      em.createNativeQuery("DELETE FROM company WHERE id NOT LIKE '01930000-%'").executeUpdate();
      em.createNativeQuery("UPDATE company SET tier = 'priority' WHERE id LIKE '01930000-%'")
          .executeUpdate();
    });
    TestSession.setup(mvc, TestSession.PASSWORD);
    session = TestSession.login(mvc, TestSession.PASSWORD);
  }

  @Test
  void startsEmptyAndPreviewDoesNotPersist() throws Exception {
    mvc.perform(get("/api/profile").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile").doesNotExist())
        .andExpect(jsonPath("$.assessment.criteriaVersion").isNumber());

    mvc.perform(
            post("/api/profile/import/preview")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"- Cargo: Dev Java\\n- Tecnologia principal: Java\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.role").value("Dev Java"))
        .andExpect(jsonPath("$.draft.primaryTechnology").value("Java"))
        .andExpect(jsonPath("$.warnings[*].field").value(hasItem("currentSeniority")))
        .andExpect(jsonPath("$.provenance.role.source").value("imported"))
        .andExpect(jsonPath("$.provenance.role.line").value(1))
        .andExpect(jsonPath("$.provenance.role.excerpt").value("- Cargo: Dev Java"))
        .andExpect(jsonPath("$.provenance.primaryTechnology.line").value(2))
        .andExpect(jsonPath("$.provenance.currentSeniority").doesNotExist())
        .andExpect(jsonPath("$.markdown").doesNotExist());

    assertThat(jdbc.sql("SELECT count(*) FROM career_profile").query(Long.class).single()).isZero();
  }

  @Test
  void rejectsInvalidMarkdownAndProfileWithoutCsrfBypass() throws Exception {
    mvc.perform(
            post("/api/profile/import/preview")
                .session(session.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"- Cargo: Dev Java\"}"))
        .andExpect(status().isForbidden());

    // IdempotencyFilter ignora /api/profile/import/preview para não memorizar o Markdown enviado.
    mvc.perform(
            post("/api/profile/import/preview")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .header("Idempotency-Key", "preview-no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"- Cargo: Dev Java\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.role").value("Dev Java"));
    mvc.perform(
            post("/api/profile/import/preview")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .header("Idempotency-Key", "preview-no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"- Cargo: Dev Kotlin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draft.role").value("Dev Kotlin"));

    mvc.perform(
            post("/api/profile/import/preview")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_profile_markdown"));

    mvc.perform(
            put("/api/profile")
                .session(session.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(0, "Java", "[\"Spring Boot\"]", "[]", "8000")))
        .andExpect(status().isForbidden());

    save(body(0, "Java", "[\"Spring Boot\"]", "[]", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_profile"));
    save(body(0, "Java", "[\"Spring Boot\"]", "[]", "8000").replace("[\"pleno\"]", "[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_profile"));
  }

  @Test
  void replacingPrioritiesDemotesRemovedCompanyWhileKeepingJustificationAndNormalizedMatch()
      throws Exception {
    save(body(0, "Java", "[]", "[]", "8000").replace("[\"Remoto\"]", "[\"SAP\", \"Itaú\"]"))
        .andExpect(status().isOk());

    save(body(1, "Java", "[]", "[]", "8000").replace("[\"Remoto\"]", "[\"Itau\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priorityCompanies[*]").value(hasItem("Itaú")));

    assertThat(
            jdbc.sql("SELECT tier, junior_justification FROM company WHERE name = 'SAP'")
                .query(
                    (rs, row) -> rs.getString("tier") + ":" + rs.getString("junior_justification"))
                .single())
        .startsWith("suggested:")
        .isNotEqualTo("suggested:null");
    assertThat(
            jdbc.sql("SELECT count(*) FROM company WHERE lower(name) LIKE 'ita%'")
                .query(Long.class)
                .single())
        .isEqualTo(1L);
  }

  @Test
  void savesVersionedProfileAlignsCriteriaAndDetectsConflicts() throws Exception {
    long criteriaBefore = criteriaVersion();

    save(body(0, "Java", "[\"Spring Boot\"]", "[\"Kotlin\"]", "8000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.version").value(1))
        .andExpect(jsonPath("$.profile.juniorFloorCltMonthlyBrl").value(8000))
        .andExpect(jsonPath("$.profile.origin").value("manual"))
        .andExpect(jsonPath("$.assessment.criteriaVersion").value(criteriaBefore + 1))
        .andExpect(jsonPath("$.assessment.state").value("pending"));

    mvc.perform(get("/api/criteria").session(session.session()))
        .andExpect(jsonPath("$.targetRoles[0]").value("Desenvolvedor Backend Java"))
        .andExpect(jsonPath("$.stackKeywords").value(hasItem("Kotlin")))
        .andExpect(jsonPath("$.version").value(criteriaBefore + 1));

    save(body(1, "Java", "[\"Spring Boot\"]", "[\"Kotlin\"]", "8000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.version").value(1));
    assertThat(criteriaVersion()).isEqualTo(criteriaBefore + 1);

    save(body(0, "Java", "[]", "[]", "8000"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("profile_version_conflict"));

    mvc.perform(get("/api/profile").session(session.session()))
        .andExpect(jsonPath("$.profile.version").value(1))
        .andExpect(jsonPath("$.profile.flexibleTechnologies[0]").value("Kotlin"))
        .andExpect(jsonPath("$.profile.updatedAt").value("2026-11-20T12:00:00Z"));
  }

  @Test
  void persistsAndReturnsFieldProvenanceWhileAcceptingClientsWithoutIt() throws Exception {
    save(body(0, "Java", "[]", "[]", "8000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.provenance").isMap())
        .andExpect(jsonPath("$.profile.provenance.role").doesNotExist());

    String tracked =
        withProvenance(
            body(1, "Java 21", "[]", "[]", "8000")
                .replace("\"origin\":\"manual\"", "\"origin\":\"markdown\""),
            """
            {"role": {"source": "inferred", "line": 3, "excerpt": "Dev | Java"},
             "primaryTechnology": {"source": "edited", "line": 3, "excerpt": "Dev | Java"},
             "juniorFloorCltMonthlyBrl": {"source": "declared", "line": null, "excerpt": null}}
            """);
    save(tracked)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.version").value(2))
        .andExpect(jsonPath("$.profile.provenance.primaryTechnology.source").value("edited"));

    mvc.perform(get("/api/profile").session(session.session()))
        .andExpect(jsonPath("$.profile.provenance.role.source").value("inferred"))
        .andExpect(jsonPath("$.profile.provenance.role.line").value(3))
        .andExpect(jsonPath("$.profile.provenance.role.excerpt").value("Dev | Java"))
        .andExpect(jsonPath("$.profile.provenance.primaryTechnology.source").value("edited"))
        .andExpect(
            jsonPath("$.profile.provenance.juniorFloorCltMonthlyBrl.source").value("declared"))
        .andExpect(jsonPath("$.profile.provenance.juniorFloorCltMonthlyBrl.line").isEmpty());
    assertThat(
            jdbc.sql("SELECT provenance FROM career_profile WHERE id = 1")
                .query(String.class)
                .single())
        .contains("Dev | Java");

    save(withProvenance(
            body(2, "Java 21", "[]", "[]", "8000"),
            "{\"role\": {\"source\": \"declared\", \"line\": 4, \"excerpt\": \"x\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_profile"));
    save(withProvenance(
            body(2, "Java 21", "[]", "[]", "8000"), "{\"salary\": {\"source\": \"declared\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_profile"));
  }

  @Test
  void juniorExceptionAcceptsSapWithConfirmedSalaryAndExcludesOtherwise() throws Exception {
    save(body(0, "Java", "[\"Spring Boot\"]", "[]", "8000")).andExpect(status().isOk());

    String sap = juniorPosting("sap-junior", "SAP");
    String noJustification = juniorPosting("picpay-junior", "PicPay");
    String sapWithoutSalary = juniorPosting("sap-no-salary", "SAP");
    announceCltSalary(sap, 9000);
    announceCltSalary(noJustification, 9000);

    assertThat(assessor.assessAndSave(sap)).isTrue();
    assertThat(assessor.assessAndSave(noJustification)).isTrue();
    assertThat(assessor.assessAndSave(sapWithoutSalary)).isTrue();

    assertThat(fit(sap)).isEqualTo("strong");
    assertThat(fit(noJustification)).isEqualTo("weak");
    assertThat(fit(sapWithoutSalary)).isEqualTo("weak");
    mvc.perform(get("/api/opportunities/" + sap).session(session.session()))
        .andExpect(
            jsonPath("$.fitReasons")
                .value(hasItem(org.hamcrest.Matchers.startsWith("Exceção júnior:"))));
  }

  @Test
  void detailGroupsPreparationAndReflectsProfileEditWithoutReassessment() throws Exception {
    save(body(0, "Java", "[\"Spring Boot\"]", "[]", "8000")).andExpect(status().isOk());
    String id = juniorPosting("prep", "CI&T");

    mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preparation.profileVersion").value(1))
        .andExpect(
            jsonPath("$.preparation.required[?(@.technology=='Java')].profileTier")
                .value(hasItem("primary")))
        .andExpect(
            jsonPath("$.preparation.required[?(@.technology=='Spring Boot')].profileTier")
                .value(hasItem("secondary")))
        .andExpect(
            jsonPath("$.preparation.optional[?(@.technology=='Docker')].profileTier")
                .value(hasItem("none")))
        .andExpect(jsonPath("$.preparation.mentioned[*].technology").value(hasItem("Kafka")))
        .andExpect(
            jsonPath("$.preparation..technology")
                .value(org.hamcrest.Matchers.not(hasItem("Python"))));

    save(body(1, "Java", "[\"Spring Boot\"]", "[\"Docker\"]", "8000")).andExpect(status().isOk());

    mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(jsonPath("$.preparation.profileVersion").value(2))
        .andExpect(
            jsonPath("$.preparation.optional[?(@.technology=='Docker')].profileTier")
                .value(hasItem("flexible")));
  }

  private String juniorPosting(String externalId, String company) {
    return ingestion.ingest(
        new JobSource.Item(
            "lever:" + externalId,
            externalId,
            "https://jobs.lever.co/example/" + externalId,
            POSTING,
            "Desenvolvedor Backend Java Júnior",
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        company);
  }

  private void announceCltSalary(String id, int min) {
    writer.run(() ->
        em.createNativeQuery(
                "UPDATE opportunity SET contract_type = 'clt', salary_unit = 'monthly', work_mode = 'remote', "
                    + "salary_min = :min, salary_max = :max WHERE id = :id")
            .setParameter("min", min)
            .setParameter("max", min + 1000)
            .setParameter("id", id)
            .executeUpdate());
  }

  private String fit(String id) {
    return jdbc.sql("SELECT fit FROM opportunity WHERE id = ?")
        .param(id)
        .query(String.class)
        .single();
  }

  private long criteriaVersion() {
    return jdbc.sql("SELECT version FROM search_criteria WHERE id = 1").query(Long.class).single();
  }

  private ResultActions save(String body) throws Exception {
    return mvc.perform(
        put("/api/profile")
            .session(session.session())
            .header("X-CSRF-Token", session.csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String withProvenance(String body, String provenance) {
    var node = (tools.jackson.databind.node.ObjectNode) JSON.readTree(body);
    node.set("provenance", JSON.readTree(provenance));
    return node.toString();
  }

  private static String body(
      long version, String primary, String secondary, String flexible, String cltFloor) {
    return JSON.readTree(
            """
            {"version": %d, "role": "Desenvolvedor Backend Java", "primaryTechnology": "%s",
             "secondaryTechnologies": %s, "flexibleTechnologies": %s,
             "currentSeniority": "pleno", "targetLevels": ["pleno"], "priorities": ["Remoto"],
             "juniorFloorCltMonthlyBrl": %s, "juniorFloorPjMonthlyBrl": null, "origin": "manual"}
            """
                .formatted(version, primary, secondary, flexible, cltFloor))
        .toString();
  }
}
