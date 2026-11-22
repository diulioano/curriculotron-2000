package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.DedupeService;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository.SourcedOpportunity;
import dev.curriculotron.opportunities.domain.JobFactExtractor;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.SearchCriteriaRepository;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** T031 — benchmark controlado com 1.000 anúncios e fontes simuladas. */
class CompatibilityPerformanceTest extends AppTestBase {
  private static final Logger log = LoggerFactory.getLogger(CompatibilityPerformanceTest.class);
  private static final int TOTAL_POSTINGS = 1_000;
  private static final long READ_THRESHOLD_MS = 2_000L;
  private static final long REASSESS_THRESHOLD_MS = 10_000L;

  @Autowired MockMvc mvc;
  @Autowired OpportunityRepository jobs;
  @Autowired DedupeService dedupe;
  @Autowired EvidenceRepository evidence;
  @Autowired SearchCriteriaRepository criteriaRepo;
  @Autowired DailySelectionService selections;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  private final JobFactExtractor extractor = new JobFactExtractor();

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), AppClock.ZONE));
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
  void readsStayUnder2sAndFullReassessmentUnder10sAcrossOneThousandPostings() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var session = TestSession.login(mvc, TestSession.PASSWORD);

    SeedStats stats = seedThousandSimulatedPostings();
    assertThat(stats.ids()).hasSize(TOTAL_POSTINGS);

    long initialReassessStart = System.nanoTime();
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ready"))
        .andExpect(jsonPath("$.assessedCount").value(TOTAL_POSTINGS))
        .andExpect(jsonPath("$.remainingCount").value(0));
    long initialReassessMs = elapsedMs(initialReassessStart);

    selections.selectToday();

    String detailId = stats.ids().getFirst();
    long homeReadBeforeMs = measureGet(session, "/api/home", "$.assessment.state", "ready");
    long selectionReadBeforeMs =
        measureGet(session, "/api/opportunities?list=selection", "$.assessment.state", "ready");
    long secondaryReadBeforeMs =
        measureGet(session, "/api/opportunities?list=secondary", "$.assessment.state", "ready");
    long discardedReadBeforeMs =
        measureGet(session, "/api/opportunities?list=discarded", "$.assessment.state", "ready");
    long detailReadBeforeMs =
        measureGet(session, "/api/opportunities/" + detailId, "$.id", detailId);

    assertThat(homeReadBeforeMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(selectionReadBeforeMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(secondaryReadBeforeMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(discardedReadBeforeMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(detailReadBeforeMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);

    String updatePayload =
        """
        {
          "version": 1,
          "targetRoles": ["Desenvolvedor Backend Pleno Python", "Python Backend Engineer"],
          "stackKeywords": ["Python", "FastAPI", "PostgreSQL", "Docker"],
          "remotePreferred": true,
          "hybridRegions": ["Porto Alegre"],
          "cltMinMonthlyBrl": 6000,
          "pjMinMonthlyBrl": 9000,
          "acceptJuniorIfJustified": true,
          "dailySearchTimeLocal": "09:00",
          "deadlineHighlightDays": 7,
          "urgentHours": 48,
          "emailDigestEnabled": false
        }
        """;
    mvc.perform(
            put("/api/criteria")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.assessment.state").value("pending"))
        .andExpect(jsonPath("$.assessment.stale").value(true));

    long pythonReassessStart = System.nanoTime();
    mvc.perform(
            post("/api/opportunities/reassess")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"criteriaVersion\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criteriaVersion").value(2))
        .andExpect(jsonPath("$.state").value("ready"))
        .andExpect(jsonPath("$.assessedCount").value(TOTAL_POSTINGS))
        .andExpect(jsonPath("$.remainingCount").value(0));
    long pythonReassessMs = elapsedMs(pythonReassessStart);

    long homeReadAfterMs = measureGet(session, "/api/home", "$.assessment.criteriaVersion", 2);
    long selectionReadAfterMs =
        measureGet(session, "/api/opportunities?list=selection", "$.assessment.criteriaVersion", 2);
    long secondaryReadAfterMs =
        measureGet(session, "/api/opportunities?list=secondary", "$.assessment.criteriaVersion", 2);
    long detailReadAfterMs =
        measureGet(session, "/api/opportunities/" + detailId, "$.compatibility.criteriaVersion", 2);

    log.info(
        "T031 Benchmark: os={} arch={} java={} cpus={} postings={} totalChars={} avgChars={} "
            + "initialReassessMs={} pythonReassessMs={} "
            + "readsBeforeMs=[home={}, selection={}, secondary={}, discarded={}, detail={}] "
            + "readsAfterMs=[home={}, selection={}, secondary={}, detail={}]",
        System.getProperty("os.name"),
        System.getProperty("os.arch"),
        System.getProperty("java.version"),
        Runtime.getRuntime().availableProcessors(),
        TOTAL_POSTINGS,
        stats.totalChars(),
        stats.totalChars() / TOTAL_POSTINGS,
        initialReassessMs,
        pythonReassessMs,
        homeReadBeforeMs,
        selectionReadBeforeMs,
        secondaryReadBeforeMs,
        discardedReadBeforeMs,
        detailReadBeforeMs,
        homeReadAfterMs,
        selectionReadAfterMs,
        secondaryReadAfterMs,
        detailReadAfterMs);

    assertThat(initialReassessMs).isLessThanOrEqualTo(REASSESS_THRESHOLD_MS);
    assertThat(pythonReassessMs).isLessThanOrEqualTo(REASSESS_THRESHOLD_MS);
    assertThat(homeReadAfterMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(selectionReadAfterMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(secondaryReadAfterMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
    assertThat(detailReadAfterMs).isLessThanOrEqualTo(READ_THRESHOLD_MS);
  }

  private long measureGet(TestSession session, String path, String jsonExpr, Object expectedValue)
      throws Exception {
    long start = System.nanoTime();
    MvcResult result =
        mvc.perform(get(path).session(session.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath(jsonExpr).value(expectedValue))
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).isNotBlank();
    return elapsedMs(start);
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private SeedStats seedThousandSimulatedPostings() {
    var current = criteriaRepo.current().criteria();
    List<String> ids = new ArrayList<>(TOTAL_POSTINGS);
    long totalChars = 0L;
    String at = clock.now().toString();
    int batchSize = 100;

    for (int offset = 0; offset < TOTAL_POSTINGS; offset += batchSize) {
      final int start = offset;
      final long[] batchChars = {0L};
      writer.run(() -> {
        for (int i = start; i < start + batchSize; i++) {
          PostingTemplate template = templateFor(i);
          String source =
              switch (i % 3) {
                case 0 -> "lever:bench";
                case 1 -> "gupy:bench";
                default -> "greenhouse:bench";
              };
          String externalId = "bench-job-" + i;
          String url = "https://boards.bench.local/" + source.replace(':', '/') + "/" + i;
          var facts =
              extractor.extract(
                  template.title(),
                  template.company(),
                  template.location(),
                  template.text(),
                  current.stack(),
                  current.hybridRegions());
          var initial =
              new SourcedOpportunity(
                  facts.title(),
                  template.company(),
                  facts.location(),
                  facts.workMode(),
                  JobFactExtractor.factList(facts),
                  "strong",
                  80 - (i % 20),
                  List.of("Critério simulado de benchmark."));
          String excerpt = template.text().substring(0, Math.min(4000, template.text().length()));
          var ingested =
              dedupe.ingestResult(
                  source, externalId, url, excerpt, () -> jobs.createFromSource(initial, at));
          String oppId = ingested.opportunityId();
          jobs.refreshDetails(
              oppId,
              facts.title(),
              template.company(),
              facts.location(),
              facts.workMode(),
              JobFactExtractor.factList(facts),
              at);
          String sourceId =
              evidence
                  .sourceId(source, externalId, url)
                  .orElseThrow(() -> new IllegalStateException("Fonte ausente no seed"));
          evidence.upsert(
              new VacancyEvidence(
                  "source-" + sourceId,
                  oppId,
                  sourceId,
                  source,
                  facts.title(),
                  template.location(),
                  template.text(),
                  template.completeness(),
                  template.completenessReason(),
                  1,
                  clock.now(),
                  url));
          if (i % 50 == 0) {
            jobs.setDecision(oppId, "discarded", at);
          }
          ids.add(oppId);
          batchChars[0] += template.text().length();
        }
      });
      totalChars += batchChars[0];
    }
    return new SeedStats(ids, totalChars);
  }

  private static PostingTemplate templateFor(int index) {
    String filler =
        "\n\nSobre a empresa e rotina:\n"
            + "Atuamos com times multidisciplinares distribuídos em território nacional, com foco em "
            + "qualidade de código, observabilidade contínua, revisão por pares, testes automatizados "
            + "e entregas incrementais semanais. Buscamos documentação clara de APIs, acompanhamento de "
            + "SLOs em produção, colaboração entre produto e engenharia e melhoria contínua da base "
            + "técnica ao longo dos ciclos trimestrais.\n"
            + "Benefícios: vale-refeição flexível, plano de saúde nacional, auxílio home office, "
            + "licença para treinamentos técnicos e horário flexível em regime remoto.\n"
            + "Processo seletivo: conversa inicial com pessoas, desafio técnico assíncrono focado em "
            + "modelagem de domínio e entrevista técnica com a liderança de engenharia.\n";

    return switch (index % 5) {
      case 0 ->
          new PostingTemplate(
              "Desenvolvedor Backend Pleno Java #" + index,
              "CWI Software",
              "Remoto",
              Completeness.complete,
              null,
              "Requisitos obrigatórios:\n"
                  + "Experiência sólida com Java 17, Spring Boot, APIs REST, Kafka, microsserviços, "
                  + "JUnit, Mockito e bancos relacionais em produção.\n"
                  + "Diferenciais desejáveis: React, Docker e Kubernetes.\n"
                  + "Regime CLT ou PJ compatível com senioridade plena em trabalho 100% remoto."
                  + filler);
      case 1 ->
          new PostingTemplate(
              "Desenvolvedor Backend Pleno Python #" + index,
              "TechBench Labs",
              "Remoto",
              Completeness.complete,
              null,
              "Requisitos obrigatórios:\n"
                  + "Experiência prática com Python 3.12, FastAPI, PostgreSQL, filas assíncronas e Docker.\n"
                  + "Não exigimos experiência prévia com Java ou Spring Boot nesta equipe.\n"
                  + "Diferenciais: observabilidade com OpenTelemetry e testes automatizados."
                  + filler);
      case 2 ->
          new PostingTemplate(
              "Product Designer Pleno #" + index,
              "StudioBench",
              "Remoto",
              Completeness.complete,
              null,
              "Requisitos obrigatórios:\n"
                  + "Atuação em pesquisa com usuários, design system no Figma, prototipação e handoff.\n"
                  + "O time de engenharia utiliza Java e React no produto principal, sem programação exigida."
                  + filler);
      case 3 ->
          new PostingTemplate(
              "Desenvolvedor Fullstack Pleno #" + index,
              "WebCorp",
              "Porto Alegre - Híbrido",
              Completeness.complete,
              null,
              "Requisitos obrigatórios:\n"
                  + "Domínio de TypeScript, React, Angular, REST e integração contínua.\n"
                  + "Diferencial: conhecimento em serviços Java ou Python no ecossistema."
                  + filler);
      default ->
          new PostingTemplate(
              "Engenheiro de Software Backend #" + index,
              "LegacyBench",
              "Remoto",
              Completeness.partial,
              "feed_summary",
              "Resumo da vaga: atuação em sistemas distribuídos remotos com foco em confiabilidade e APIs."
                  + filler);
    };
  }

  private record PostingTemplate(
      String title,
      String company,
      String location,
      Completeness completeness,
      String completenessReason,
      String text) {}

  private record SeedStats(List<String> ids, long totalChars) {}
}
