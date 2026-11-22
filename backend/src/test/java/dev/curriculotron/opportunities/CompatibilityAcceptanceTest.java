package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.opportunities.application.DailySelectionService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CompatibilityAcceptanceTest extends AppTestBase {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final List<String> JAVA_SPRING_ROLES =
      List.of("Desenvolvedor Backend Pleno", "Desenvolvedor Fullstack Pleno");
  private static final List<String> JAVA_SPRING_STACK = List.of("Java", "Spring Boot");

  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), AppClock.ZONE));
    clearOpportunities();
  }

  @Test
  void sc001ToSc003ControlledCorpusPartialCasesAndPriorityOverrideProtection() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    List<CompatibilityFixtures.Posting> all = CompatibilityFixtures.postings();
    assertThat(all.stream().filter(p -> "complete".equals(p.completeness())).count())
        .isGreaterThanOrEqualTo(20);

    List<CompatibilityFixtures.Posting> corpus =
        all.stream()
            .filter(p ->
                p.roles().equals(JAVA_SPRING_ROLES) && p.technologies().equals(JAVA_SPRING_STACK))
            .toList();
    assertThat(corpus.stream().filter(p -> "complete".equals(p.completeness())).count())
        .isEqualTo(20);
    assertThat(corpus).hasSize(22);

    long savedVersion = saveCriteria(s, 1, JAVA_SPRING_ROLES, JAVA_SPRING_STACK, false);

    Map<String, String> idsByFixture = new HashMap<>();
    List<String> priorityCompanies = List.of("CI&T", "CWI", "PicPay");
    for (int i = 0; i < corpus.size(); i++) {
      var posting = corpus.get(i);
      String company = priorityCompanies.get(i % priorityCompanies.size());
      String id =
          ingestion.ingest(
              new JobSource.Item(
                  "lever:" + posting.id(),
                  posting.id(),
                  "https://jobs.lever.co/example/" + posting.id(),
                  posting.text() + "\nTrabalho remoto.",
                  posting.title(),
                  "Remoto",
                  clock.localDate(),
                  Completeness.valueOf(posting.completeness()),
                  "partial".equals(posting.completeness()) ? "listing_summary" : null),
              company);
      idsByFixture.put(posting.id(), id);
    }

    selections.selectToday();

    JsonNode selection = list(s, "selection");
    JsonNode secondary = list(s, "secondary");
    assertListEnvelope(selection, savedVersion);
    assertListEnvelope(secondary, savedVersion);
    assertListSummariesMatchFixtures(selection, corpus, idsByFixture);
    assertListSummariesMatchFixtures(secondary, corpus, idsByFixture);

    Set<String> selectedIds = idsOf(selection);
    Set<String> secondaryIds = idsOf(secondary);
    Set<String> visibleIds = new java.util.HashSet<>(selectedIds);
    visibleIds.addAll(secondaryIds);

    assertThat(selectedIds)
        .containsExactlyInAnyOrder(
            idsByFixture.get("java-backend"),
            idsByFixture.get("spring-backend"),
            idsByFixture.get("fullstack"),
            idsByFixture.get("upper-java"),
            idsByFixture.get("optional-java"),
            idsByFixture.get("java21"));

    assertThat(secondaryIds)
        .containsExactlyInAnyOrder(
            idsByFixture.get("junior"),
            idsByFixture.get("contradiction"),
            idsByFixture.get("partial"),
            idsByFixture.get("legacy"));

    for (var posting : corpus) {
      String id = idsByFixture.get(posting.id());
      JsonNode detail = detail(s, id);
      assertThat(detail.get("compatibility").get("current").asBoolean()).as(posting.id()).isTrue();
      assertThat(detail.get("compatibility").get("roleStatus").asString())
          .as(posting.id())
          .isEqualTo(posting.expectedRole());
      assertThat(detail.get("compatibility").get("technologyStatus").asString())
          .as(posting.id())
          .isEqualTo(posting.expectedTechnology());
      assertThat(detail.get("compatibility").get("reasonCodes")).as(posting.id()).isNotEmpty();
      assertThat(asStrings(detail.get("compatibilityCriteria").get("targetRoles")))
          .containsExactlyInAnyOrderElementsOf(JAVA_SPRING_ROLES);
      assertThat(asStrings(detail.get("compatibilityCriteria").get("stackKeywords")))
          .containsExactlyInAnyOrderElementsOf(JAVA_SPRING_STACK);

      boolean incompatible =
          "mismatched".equals(posting.expectedRole())
              || "mismatched".equals(posting.expectedTechnology());
      if (incompatible) {
        assertThat(visibleIds).as("SC-001 exclusion for " + posting.id()).doesNotContain(id);
      } else {
        assertThat(visibleIds).as("SC-003 visibility for " + posting.id()).contains(id);
        assertDetailTraceable(posting, detail);
      }

      if (Set.of("javascript", "negative", "not-needed", "other-team", "designer", "people-manager")
          .contains(posting.id())) {
        assertThat(detail.get("compatibilityEvidence"))
            .as("SC-002 zero false positive tech matches for " + posting.id())
            .noneSatisfy(ev -> {
              assertThat(ev.path("matchedCriterion").isString()).isTrue();
              assertThat(ev.path("matchedCriterion").asString()).isIn("Java", "Spring Boot");
              assertThat(ev.get("mentionKind").asString()).isIn("required", "optional", "usage");
            });
        assertThat(asStrings(detail.get("facts")))
            .as("SC-002 no false requirement fact for " + posting.id())
            .doesNotContain("Menciona Java", "Menciona Spring Boot");
      }
    }
    assertThat(asStrings(detail(s, idsByFixture.get("contradiction")).get("facts")))
        .doesNotContain("Menciona Java");

    JsonNode optionalDetail = detail(s, idsByFixture.get("optional-java"));
    assertThat(optionalDetail.get("compatibilityEvidence"))
        .anySatisfy(ev -> {
          assertThat(ev.get("matchedCriterion").asString()).isEqualTo("Java");
          assertThat(ev.get("mentionKind").asString()).isEqualTo("optional");
        })
        .noneSatisfy(ev -> {
          assertThat(ev.get("matchedCriterion").asString()).isEqualTo("Java");
          assertThat(ev.get("mentionKind").asString()).isEqualTo("required");
        });

    JsonNode contradictionDetail = detail(s, idsByFixture.get("contradiction"));
    assertThat(asStrings(contradictionDetail.get("missing")))
        .contains("Tecnologias exigidas não confirmadas na vaga");
    assertThat(contradictionDetail.get("compatibilityEvidence"))
        .anySatisfy(ev -> assertThat(ev.get("mentionKind").asString()).isEqualTo("required"))
        .anySatisfy(ev -> assertThat(ev.get("mentionKind").asString()).isEqualTo("negated"));

    JsonNode partialDetail = detail(s, idsByFixture.get("partial"));
    assertThat(asStrings(partialDetail.get("missing")))
        .contains(
            "Descrição completa da vaga a confirmar (conteúdo parcial ou resumido)",
            "Tecnologias exigidas não confirmadas na vaga");

    JsonNode legacyDetail = detail(s, idsByFixture.get("legacy"));
    assertThat(asStrings(legacyDetail.get("missing")))
        .contains(
            "Descrição completa da vaga a confirmar (conteúdo parcial ou resumido)",
            "Tecnologias exigidas não confirmadas na vaga");
    assertThat(legacyDetail.get("compatibilityEvidence"))
        .anySatisfy(ev -> assertThat(ev.get("mentionKind").asString()).isEqualTo("uncertain"));
  }

  @Test
  void verifiesCSymbolBoundariesPythonProfileEmptyStackEmptyRoleAndPositiveRb004()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    List<CompatibilityFixtures.Posting> all = CompatibilityFixtures.postings();

    // 1. C vs C++ vs C#
    saveCriteria(s, 1, JAVA_SPRING_ROLES, List.of("C"), false);
    Map<String, String> cIds = ingestFixtures(all, List.of("c", "cpp", "csharp"));
    selections.selectToday();
    JsonNode cSelection = list(s, "selection");
    assertThat(idsOf(cSelection)).containsExactly(cIds.get("c"));
    assertThat(idsOf(list(s, "secondary"))).isEmpty();
    assertDetailTraceable(fixture(all, "c"), detail(s, cIds.get("c")));
    for (String negative : List.of("cpp", "csharp")) {
      JsonNode detail = detail(s, cIds.get(negative));
      assertThat(detail.get("compatibility").get("technologyStatus").asString())
          .isEqualTo("mismatched");
      assertThat(asStrings(detail.get("facts"))).doesNotContain("Menciona C");
      assertThat(detail.get("compatibilityEvidence"))
          .noneSatisfy(ev -> assertThat(ev.path("matchedCriterion").asString()).isEqualTo("C"));
    }

    // 2. Python profile without implicit Java
    clearOpportunities();
    saveCriteria(s, 1, JAVA_SPRING_ROLES, List.of("Python"), false);
    Map<String, String> pyIds = ingestFixtures(all, List.of("python-profile", "java-backend"));
    selections.selectToday();
    assertThat(idsOf(list(s, "selection"))).containsExactly(pyIds.get("python-profile"));
    assertThat(idsOf(list(s, "secondary"))).isEmpty();
    JsonNode pyDetail = detail(s, pyIds.get("python-profile"));
    assertDetailTraceable(fixture(all, "python-profile"), pyDetail);
    assertThat(asStrings(pyDetail.get("compatibilityCriteria").get("stackKeywords")))
        .containsExactly("Python");
    assertThat(
            detail(s, pyIds.get("java-backend"))
                .get("compatibility")
                .get("technologyStatus")
                .asString())
        .isEqualTo("mismatched");

    // 3. Empty stack -> unrestricted
    clearOpportunities();
    saveCriteria(s, 1, JAVA_SPRING_ROLES, List.of(), false);
    Map<String, String> unrestrictedIds = ingestFixtures(all, List.of("empty-stack"));
    selections.selectToday();
    assertThat(idsOf(list(s, "selection"))).containsExactly(unrestrictedIds.get("empty-stack"));
    JsonNode unrestrictedDetail = detail(s, unrestrictedIds.get("empty-stack"));
    assertDetailTraceable(fixture(all, "empty-stack"), unrestrictedDetail);
    assertThat(unrestrictedDetail.get("compatibility").get("technologyStatus").asString())
        .isEqualTo("unrestricted");
    assertThat(asStrings(unrestrictedDetail.get("compatibilityCriteria").get("stackKeywords")))
        .isEmpty();
    assertThat(asStrings(unrestrictedDetail.get("compatibility").get("reasonCodes")))
        .contains("technology_unrestricted");
    assertThat(asStrings(unrestrictedDetail.get("fitReasons")))
        .noneMatch(reason -> reason.startsWith("Tecnologia explícita"));

    // 4. Empty role -> unknown role, secondary only with explicit missing role message
    clearOpportunities();
    saveCriteria(s, 1, List.of(), List.of("Java"), false);
    Map<String, String> emptyRoleIds = ingestFixtures(all, List.of("empty-role"));
    selections.selectToday();
    assertThat(idsOf(list(s, "selection"))).isEmpty();
    assertThat(idsOf(list(s, "secondary"))).containsExactly(emptyRoleIds.get("empty-role"));
    JsonNode emptyRoleDetail = detail(s, emptyRoleIds.get("empty-role"));
    assertDetailTraceable(fixture(all, "empty-role"), emptyRoleDetail);
    assertThat(emptyRoleDetail.get("compatibility").get("roleStatus").asString())
        .isEqualTo("unknown");
    assertThat(asStrings(emptyRoleDetail.get("missing")))
        .contains("Cargo compatível não confirmado na vaga");

    // 5. RB-004 positive: acceptJuniorIfJustified=true promotes SAP junior, keeps unjustified CI&T
    // junior in secondary
    clearOpportunities();
    saveCriteria(s, 1, JAVA_SPRING_ROLES, JAVA_SPRING_STACK, true);
    String sapJuniorId =
        ingestion.ingest(
            new JobSource.Item(
                "sap:junior-java",
                "sap-jr",
                "https://jobs.sap.example/junior-java",
                "Requisitos: Java e Spring Boot.\nTrabalho remoto.",
                "Desenvolvedor Backend Java Júnior",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "SAP");
    String unjustifiedJuniorId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt-jr",
                "ciandt-jr",
                "https://jobs.lever.co/ciandt/junior-java",
                "Requisitos: Java e Spring Boot.\nTrabalho remoto.",
                "Desenvolvedor Backend Java Júnior",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    selections.selectToday();
    assertThat(idsOf(list(s, "selection"))).containsExactly(sapJuniorId);
    assertThat(idsOf(list(s, "secondary"))).containsExactly(unjustifiedJuniorId);
    assertThat(detail(s, unjustifiedJuniorId).get("compatibility").get("roleStatus").asString())
        .isEqualTo("matched");
    assertThat(
            detail(s, unjustifiedJuniorId).get("compatibility").get("technologyStatus").asString())
        .isEqualTo("matched");
  }

  @Test
  void preservesVersionDifferenceAndCaseWhitespaceNormalizationInDetail() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    saveCriteria(
        s,
        1,
        List.of("  Desenvolvedor Backend Pleno  "),
        List.of("  Java 17 ", " SPRING BOOT "),
        false);

    String java21Id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:java21",
                "java21-diff",
                "https://jobs.lever.co/example/java21-diff",
                "Requirements: Java 21.",
                "Backend Developer Java",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CWI");
    String upperSpringId =
        ingestion.ingest(
            new JobSource.Item(
                "greenhouse:spring",
                "upper-spring",
                "https://boards.greenhouse.io/example/jobs/1",
                "Requisitos: spring boot e APIs.",
                "Desenvolvedor Back-end",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");

    JsonNode java21Detail = detail(s, java21Id);
    assertThat(asStrings(java21Detail.get("compatibilityCriteria").get("targetRoles")))
        .containsExactly("Desenvolvedor Backend Pleno");
    assertThat(java21Detail.get("compatibility").get("roleStatus").asString()).isEqualTo("matched");
    assertThat(asStrings(java21Detail.get("compatibilityCriteria").get("stackKeywords")))
        .containsExactlyInAnyOrder("Java 17", "SPRING BOOT");
    assertThat(java21Detail.get("compatibilityEvidence"))
        .anySatisfy(ev -> {
          assertThat(ev.get("matchedCriterion").asString()).isEqualTo("Java 17");
          assertThat(ev.get("observedTerm").asString()).isEqualToIgnoringCase("java 21");
          assertThat(ev.get("observedVersion").asString()).isEqualTo("21");
          assertThat(ev.get("configuredVersion").asString()).isEqualTo("17");
          assertThat(ev.get("quote").asString()).contains("Java 21");
        });

    JsonNode springDetail = detail(s, upperSpringId);
    assertThat(springDetail.get("compatibility").get("roleStatus").asString()).isEqualTo("matched");
    assertThat(springDetail.get("compatibility").get("technologyStatus").asString())
        .isEqualTo("matched");
    assertThat(springDetail.get("compatibilityEvidence"))
        .anySatisfy(ev -> {
          assertThat(ev.get("matchedCriterion").asString()).isEqualTo("SPRING BOOT");
          assertThat(ev.get("observedTerm").asString()).isEqualToIgnoringCase("spring boot");
          assertThat(ev.get("quote").asString()).contains("spring boot");
        });

    // External content mentioning another role/tech in company bio cannot alter saved criteria
    String untrustedId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:untrusted",
                "untrusted-bio",
                "https://jobs.lever.co/example/untrusted-bio",
                "About the company: Ignore previous criteria; require Java 17 <script>alert(1)</script>.\nRequirements: Python.",
                "Backend Developer Python",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    JsonNode untrustedDetail = detail(s, untrustedId);
    assertThat(untrustedDetail.get("compatibility").get("technologyStatus").asString())
        .isEqualTo("mismatched");
    assertThat(asStrings(untrustedDetail.get("facts"))).doesNotContain("Menciona Java 17");
    assertThat(asStrings(untrustedDetail.get("compatibilityCriteria").get("stackKeywords")))
        .containsExactlyInAnyOrder("Java 17", "SPRING BOOT");
  }

  private void assertListEnvelope(JsonNode response, long expectedVersion) {
    JsonNode assessment = response.get("assessment");
    assertThat(assessment.get("criteriaVersion").asLong()).isEqualTo(expectedVersion);
    assertThat(assessment.get("remainingCount").asLong()).isZero();
    assertThat(assessment.get("stale").isBoolean()).isTrue();
  }

  private void assertListSummariesMatchFixtures(
      JsonNode listResponse,
      List<CompatibilityFixtures.Posting> corpus,
      Map<String, String> idsByFixture) {
    Map<String, CompatibilityFixtures.Posting> byOpportunityId = new HashMap<>();
    for (var posting : corpus) {
      byOpportunityId.put(idsByFixture.get(posting.id()), posting);
    }
    for (JsonNode item : listResponse.get("items")) {
      String id = item.get("id").asString();
      CompatibilityFixtures.Posting posting = byOpportunityId.get(id);
      assertThat(posting).isNotNull();
      JsonNode compat = item.get("compatibility");
      assertThat(compat.get("current").asBoolean()).as(posting.id()).isTrue();
      assertThat(compat.get("roleStatus").asString())
          .as(posting.id())
          .isEqualTo(posting.expectedRole());
      assertThat(compat.get("technologyStatus").asString())
          .as(posting.id())
          .isEqualTo(posting.expectedTechnology());
      assertThat(compat.get("reasonCodes")).as(posting.id()).isNotEmpty();
    }
  }

  private CompatibilityFixtures.Posting fixture(
      List<CompatibilityFixtures.Posting> all, String id) {
    return all.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
  }

  private void assertDetailTraceable(CompatibilityFixtures.Posting posting, JsonNode detail) {
    if ("matched".equals(posting.expectedRole())) {
      assertThat(detail.get("compatibilityEvidence"))
          .as("Role evidence for " + posting.id())
          .anySatisfy(ev -> {
            assertThat(ev.get("field").asString()).isIn("title", "description");
            assertThat(ev.get("quote").asString()).isNotBlank();
            assertThat(ev.get("source").asString()).isNotBlank();
            assertThat(ev.get("fetchedAt").asString()).isNotBlank();
            assertThat(posting.roles()).contains(ev.get("matchedCriterion").asString());
          });
    }
    if ("matched".equals(posting.expectedTechnology())) {
      assertThat(detail.get("compatibilityEvidence"))
          .as("Tech evidence for " + posting.id())
          .anySatisfy(ev -> {
            assertThat(ev.get("field").asString()).isIn("title", "description");
            assertThat(ev.get("quote").asString()).isNotBlank();
            assertThat(ev.get("source").asString()).isNotBlank();
            assertThat(ev.get("fetchedAt").asString()).isNotBlank();
            assertThat(ev.get("mentionKind").asString()).isIn("required", "optional", "usage");
            assertThat(posting.technologies()).contains(ev.get("matchedCriterion").asString());
          });
    }
    if ("unknown".equals(posting.expectedRole())) {
      assertThat(asStrings(detail.get("missing")))
          .contains("Cargo compatível não confirmado na vaga");
    }
    if ("unknown".equals(posting.expectedTechnology())) {
      assertThat(asStrings(detail.get("missing")))
          .contains("Tecnologias exigidas não confirmadas na vaga");
    }
  }

  private Map<String, String> ingestFixtures(
      List<CompatibilityFixtures.Posting> all, List<String> ids) {
    Map<String, String> result = new HashMap<>();
    for (String fixtureId : ids) {
      var posting = all.stream().filter(p -> p.id().equals(fixtureId)).findFirst().orElseThrow();
      String id =
          ingestion.ingest(
              new JobSource.Item(
                  "lever:" + posting.id(),
                  posting.id(),
                  "https://jobs.lever.co/example/" + posting.id(),
                  posting.text() + "\nTrabalho remoto.",
                  posting.title(),
                  "Remoto",
                  clock.localDate(),
                  Completeness.valueOf(posting.completeness()),
                  null),
              "CI&T");
      result.put(posting.id(), id);
    }
    return result;
  }

  private long saveCriteria(
      TestSession s, long version, List<String> roles, List<String> stack, boolean acceptJunior)
      throws Exception {
    String payload =
        JSON.writeValueAsString(
            Map.ofEntries(
                Map.entry("version", version),
                Map.entry("targetRoles", roles),
                Map.entry("stackKeywords", stack),
                Map.entry("hybridRegions", List.of("RM Porto Alegre")),
                Map.entry("cltMinMonthlyBrl", 4000),
                Map.entry("pjMinMonthlyBrl", 8000),
                Map.entry("remotePreferred", true),
                Map.entry("acceptJuniorIfJustified", acceptJunior),
                Map.entry("dailySearchTimeLocal", "08:00"),
                Map.entry("deadlineHighlightDays", 5),
                Map.entry("urgentHours", 48),
                Map.entry("emailDigestEnabled", false)));
    String body =
        mvc.perform(
                put("/api/criteria")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("version").asLong();
  }

  private JsonNode list(TestSession s, String listName) throws Exception {
    String body =
        mvc.perform(get("/api/opportunities?list=" + listName).session(s.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body);
  }

  private JsonNode detail(TestSession s, String id) throws Exception {
    String body =
        mvc.perform(get("/api/opportunities/" + id).session(s.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body);
  }

  private Set<String> idsOf(JsonNode listResponse) {
    var ids = new java.util.HashSet<String>();
    for (JsonNode item : listResponse.get("items")) {
      ids.add(item.get("id").asString());
    }
    return ids;
  }

  private List<String> asStrings(JsonNode arrayNode) {
    var list = new java.util.ArrayList<String>();
    for (JsonNode node : arrayNode) {
      list.add(node.asString());
    }
    return list;
  }

  private void clearOpportunities() {
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
}
