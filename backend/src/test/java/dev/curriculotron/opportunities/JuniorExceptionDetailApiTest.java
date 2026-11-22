package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** US006: o detalhe expõe a decisão tipada da exceção júnior usada pelo ranking. */
class JuniorExceptionDetailApiTest extends AppTestBase {
  private static final String POSTING =
      """
      Requisitos:
      Experiência com Java e Spring Boot.
      """;

  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired AssessOpportunityService assessor;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;
  @Autowired JdbcClient jdbc;

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
    });
    TestSession.setup(mvc, TestSession.PASSWORD);
    session = TestSession.login(mvc, TestSession.PASSWORD);
  }

  @Test
  void isUnknownWithoutSavedProfile() throws Exception {
    String id = posting("sem-perfil", "SAP", "Desenvolvedor Backend Java Júnior");

    detail(id).andExpect(jsonPath("$.juniorException.status").value("unknown"));
  }

  @Test
  void keepsCompanyReasonWhenOnlySalaryFails() throws Exception {
    saveProfile("[\"pleno\"]");
    String id = posting("sap-abaixo", "SAP", "Desenvolvedor Backend Java Júnior");
    announceClt(id, 5000, 6000);
    assertThat(assessor.assessAndSave(id)).isTrue();

    assertThat(fit(id)).isEqualTo("weak");
    detail(id)
        .andExpect(jsonPath("$.juniorException.status").value("excluded"))
        .andExpect(jsonPath("$.juniorException.workFit").value(true))
        .andExpect(jsonPath("$.juniorException.salary.status").value("below_floor"))
        .andExpect(jsonPath("$.juniorException.salary.source").value("announced"))
        .andExpect(jsonPath("$.juniorException.salary.contract").value("clt"))
        .andExpect(jsonPath("$.juniorException.salary.floor").value(7000))
        .andExpect(jsonPath("$.juniorException.salary.announcedMin").value(5000))
        .andExpect(jsonPath("$.juniorException.justification.status").value("accepted"))
        .andExpect(jsonPath("$.juniorException.justification.text").isNotEmpty());
  }

  @Test
  void qualifiesWithConfirmedSalaryAndAcceptedReason() throws Exception {
    saveProfile("[\"pleno\"]");
    String id = posting("sap-ok", "SAP", "Desenvolvedor Backend Java Júnior");
    announceClt(id, 8000, 9000);
    assertThat(assessor.assessAndSave(id)).isTrue();

    assertThat(fit(id)).isEqualTo("strong");
    detail(id)
        .andExpect(jsonPath("$.juniorException.status").value("qualified"))
        .andExpect(jsonPath("$.juniorException.salary.status").value("confirmed"));
  }

  @Test
  void reportsMissingCompanyReasonAndUnannouncedSalary() throws Exception {
    saveProfile("[\"pleno\"]");
    String picpay = posting("picpay", "PicPay", "Desenvolvedor Backend Java Júnior");
    announceClt(picpay, 9000, 10000);
    String noSalary = posting("sap-sem-salario", "SAP", "Desenvolvedor Backend Java Júnior");
    writer.run(() ->
        em.createNativeQuery("UPDATE opportunity SET contract_type = 'clt' WHERE id = :id")
            .setParameter("id", noSalary)
            .executeUpdate());

    detail(picpay)
        .andExpect(jsonPath("$.juniorException.justification.status").value("missing"))
        .andExpect(jsonPath("$.juniorException.justification.text").isEmpty())
        .andExpect(jsonPath("$.juniorException.salary.status").value("confirmed"));
    detail(noSalary)
        .andExpect(jsonPath("$.juniorException.status").value("excluded"))
        .andExpect(jsonPath("$.juniorException.salary.status").value("not_announced"))
        .andExpect(jsonPath("$.juniorException.salary.source").value("none"));
  }

  @Test
  void juniorFloorOverridesGeneralPjMinimumWhenLower() throws Exception {
    saveProfile("[\"pleno\"]", 0, 7000, 8000);
    String id = posting("sap-pj-piso-proprio", "SAP", "Desenvolvedor Backend Java Júnior");
    announce(id, "pj", 8500, 8500);
    assertThat(assessor.assessAndSave(id)).isTrue();

    assertThat(fit(id)).isEqualTo("strong");
    mvc.perform(get("/api/opportunities?list=recommended").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].id", hasItem(id)));
    detail(id)
        .andExpect(jsonPath("$.juniorException.status").value("qualified"))
        .andExpect(jsonPath("$.juniorException.workFit").value(true))
        .andExpect(jsonPath("$.juniorException.salary.status").value("confirmed"))
        .andExpect(jsonPath("$.juniorException.salary.contract").value("pj"))
        .andExpect(jsonPath("$.juniorException.salary.floor").value(8000))
        .andExpect(jsonPath("$.juniorException.salary.announcedMin").value(8500))
        .andExpect(jsonPath("$.juniorException.justification.status").value("accepted"));
  }

  @Test
  void notApplicableOutsideRankingJuniorLevelOrWhenJuniorIsTarget() throws Exception {
    saveProfile("[\"pleno\"]");
    String pleno = posting("pleno", "SAP", "Desenvolvedor Backend Java Pleno");
    String junior = posting("junior-alvo", "SAP", "Desenvolvedor Backend Java Júnior");

    detail(pleno).andExpect(jsonPath("$.juniorException.status").value("not_applicable"));
    saveProfile("[\"junior\", \"pleno\"]", 1);
    detail(junior).andExpect(jsonPath("$.juniorException.status").value("not_applicable"));
  }

  private String fit(String id) {
    return jdbc.sql("SELECT fit FROM opportunity WHERE id = ?")
        .param(id)
        .query(String.class)
        .single();
  }

  private ResultActions detail(String id) throws Exception {
    return mvc.perform(get("/api/opportunities/" + id).session(session.session()))
        .andExpect(status().isOk());
  }

  private String posting(String externalId, String company, String title) {
    return ingestion.ingest(
        new JobSource.Item(
            "lever:" + externalId,
            externalId,
            "https://jobs.lever.co/example/" + externalId,
            POSTING,
            title,
            "Remoto",
            clock.localDate(),
            Completeness.complete,
            null),
        company);
  }

  private void announceClt(String id, int min, int max) {
    announce(id, "clt", min, max);
  }

  private void announce(String id, String contract, int min, int max) {
    writer.run(() ->
        em.createNativeQuery(
                "UPDATE opportunity SET contract_type = :contract, salary_unit = 'monthly', "
                    + "work_mode = 'remote', salary_min = :min, salary_max = :max WHERE id = :id")
            .setParameter("contract", contract)
            .setParameter("min", min)
            .setParameter("max", max)
            .setParameter("id", id)
            .executeUpdate());
  }

  private void saveProfile(String targetLevels) throws Exception {
    saveProfile(targetLevels, 0, 7000, null);
  }

  private void saveProfile(String targetLevels, long version) throws Exception {
    saveProfile(targetLevels, version, 7000, null);
  }

  private void saveProfile(String targetLevels, long version, Integer cltFloor, Integer pjFloor)
      throws Exception {
    mvc.perform(
            put("/api/profile")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version": %d, "role": "Desenvolvedor Backend Java", "primaryTechnology": "Java",
                     "secondaryTechnologies": ["Spring Boot"], "flexibleTechnologies": [],
                     "currentSeniority": "pleno", "targetLevels": %s, "priorities": [],
                     "juniorFloorCltMonthlyBrl": %s, "juniorFloorPjMonthlyBrl": %s,
                     "origin": "manual"}
                    """
                        .formatted(version, targetLevels, cltFloor, pjFloor)))
        .andExpect(status().isOk());
  }
}
