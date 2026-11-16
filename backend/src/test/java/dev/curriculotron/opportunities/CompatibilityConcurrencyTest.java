package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.opportunities.application.AssessmentInputService;
import dev.curriculotron.opportunities.application.CompatibilityRefreshService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.DedupeRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.TaskRunTrigger;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.sources.JobSourceFactory;
import dev.curriculotron.sources.SearchService;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class CompatibilityConcurrencyTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired OpportunityService jobs;
  @Autowired SelectionRepository selectionRepository;
  @Autowired CompatibilityRepository assessments;
  @Autowired EvidenceRepository evidence;
  @Autowired DedupeRepository dedupeRepository;
  @Autowired CompatibilityRefreshService refresh;
  @Autowired SearchService search;
  @Autowired TaskRunService tasks;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @MockitoBean JobSourceFactory factory;
  @MockitoSpyBean AssessmentInputService inputs;

  private final AtomicReference<Consumer<String>> snapshotHook = new AtomicReference<>();

  @BeforeEach
  void reset() {
    snapshotHook.set(null);
    doAnswer(
            inv -> {
              Object result = inv.callRealMethod();
              Consumer<String> hook = snapshotHook.get();
              if (hook != null) {
                hook.accept(inv.getArgument(0));
              }
              return result;
            })
        .when(inputs)
        .snapshot(anyString());
    when(factory.forAdapter(anyString()))
        .thenReturn(() -> new JobSource.SourceResult(List.of(), List.of(), List.of()));

    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM company").executeUpdate();
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_evidence").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_url_identity").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_external_identity").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_source").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_match_candidate").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity").executeUpdate();
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

  @AfterEach
  void clearHook() {
    snapshotHook.set(null);
  }

  @Test
  void putCriteriaDuringSearchLeavesZeroObsoleteCurrentAssessmentsAndSkipsStaleSelection()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    writer.run(() -> {
      em.createNativeQuery(
              "INSERT INTO company(id,name,tier,careers_adapter,created_at,updated_at) VALUES ('c1','Board One','priority','lever:one','2026-11-15T10:00:00Z','2026-11-15T10:00:00Z')")
          .executeUpdate();
      em.createNativeQuery(
              "INSERT INTO company(id,name,tier,careers_adapter,created_at,updated_at) VALUES ('c2','Board Two','suggested','lever:two','2026-11-15T10:00:00Z','2026-11-15T10:00:00Z')")
          .executeUpdate();
    });

    CountDownLatch secondBoardEntered = new CountDownLatch(1);
    CountDownLatch allowSecondBoardFinish = new CountDownLatch(1);

    when(factory.forAdapter("lever:one"))
        .thenReturn(() ->
            new JobSource.SourceResult(
                List.of(
                    new JobSource.Item(
                        "lever:one",
                        "search-java-1",
                        "https://search-race.test/1",
                        "Requisitos: Java e Spring Boot.",
                        "Desenvolvedor Backend Pleno",
                        "Remoto",
                        clock.localDate(),
                        Completeness.complete,
                        null)),
                List.of(),
                List.of()));
    when(factory.forAdapter("lever:two"))
        .thenReturn(() -> {
          secondBoardEntered.countDown();
          try {
            assertThat(allowSecondBoardFinish.await(5, TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new JobSource.SourceResult(
              List.of(
                  new JobSource.Item(
                      "lever:two",
                      "search-java-2",
                      "https://search-race.test/2",
                      "Requisitos: Java.",
                      "Desenvolvedor Backend Pleno",
                      "Remoto",
                      clock.localDate(),
                      Completeness.complete,
                      null)),
              List.of(),
              List.of());
        });

    String runId = search.submit("race-search-" + System.nanoTime(), TaskRunTrigger.manual);
    assertThat(secondBoardEntered.await(5, TimeUnit.SECONDS)).isTrue();

    // Update criteria Java -> Python while SearchService is running between board 1 and selection.
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
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));

    allowSecondBoardFinish.countDown();
    waitUntilSearchFinishes(runId);

    // Neither v1 Java job nor v2 mismatched Java job should enter selection under Python v2,
    // and the v1 job must never appear as current=true.
    assertThat(selectionRepository.selected(clock.localDate())).isEmpty();
    mvc.perform(get("/api/opportunities/compatibility-status").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criteriaVersion").value(2))
        .andExpect(jsonPath("$.state").value("pending"))
        .andExpect(jsonPath("$.stale").value(true))
        .andExpect(jsonPath("$.remainingCount").value(1));

    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    String secondaryJson =
        mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((List<?>) JsonPath.read(secondaryJson, "$.items")).isEmpty();

    // First job assessed at v1 is marked current=false when viewed directly.
    List<String> ids =
        em.createQuery("select o.id from OpportunityEntity o", String.class).getResultList();
    assertThat(ids).hasSize(2);
    for (String id : ids) {
      var assessment = assessments.find(id).orElseThrow();
      if (assessment.criteriaVersion() == 1) {
        mvc.perform(get("/api/opportunities/" + id).session(s.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compatibility.current").value(false))
            .andExpect(jsonPath("$.compatibility.criteriaVersion").value(1));
      } else {
        assertThat(assessment.criteriaVersion()).isEqualTo(2);
        assertThat(assessment.technologyStatus()).isEqualTo(Axis.mismatched);
      }
    }
  }

  @Test
  void putCriteriaDuringReassessmentReturnsConflictAndNeverMarksObsoleteAssessmentCurrent()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String javaOpp =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "race-put-reassess",
                "https://race-put.test/1",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Race Co");
    // Reset compatibility to trigger reassess on v1.
    writer.run(() ->
        em.createNativeQuery("DELETE FROM opportunity_compatibility WHERE opportunity_id = :id")
            .setParameter("id", javaOpp)
            .executeUpdate());

    CountDownLatch enteredSnapshot = new CountDownLatch(1);
    CountDownLatch allowResume = new CountDownLatch(1);
    snapshotHook.set(id -> {
      if (id.equals(javaOpp)) {
        enteredSnapshot.countDown();
        try {
          assertThat(allowResume.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<MvcResult> reassessFuture =
          executor.submit(() ->
              mvc.perform(
                      post("/api/opportunities/reassess")
                          .session(s.session())
                          .header("X-CSRF-Token", s.csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"criteriaVersion\":1}"))
                  .andReturn());

      assertThat(enteredSnapshot.await(5, TimeUnit.SECONDS)).isTrue();

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
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(2));

      allowResume.countDown();
      MvcResult result = reassessFuture.get(5, TimeUnit.SECONDS);
      assertThat(result.getResponse().getStatus()).isEqualTo(409);
      assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.error.code"))
          .isEqualTo("criteria_version_conflict");

      assertThat(assessments.find(javaOpp)).isEmpty();
      mvc.perform(get("/api/opportunities/compatibility-status").session(s.session()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.criteriaVersion").value(2))
          .andExpect(jsonPath("$.state").value("pending"))
          .andExpect(jsonPath("$.stale").value(true))
          .andExpect(jsonPath("$.remainingCount").value(1));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void simultaneousReassessPostsRejectConcurrentRequestWith409AndFinishFirstCleanly()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String opp =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "race-post-1",
                "https://race-post.test/1",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Race Co");
    writer.run(() ->
        em.createNativeQuery("DELETE FROM opportunity_compatibility WHERE opportunity_id = :id")
            .setParameter("id", opp)
            .executeUpdate());

    CountDownLatch enteredFirst = new CountDownLatch(1);
    CountDownLatch allowFirstFinish = new CountDownLatch(1);
    snapshotHook.set(id -> {
      if (id.equals(opp)) {
        enteredFirst.countDown();
        try {
          assertThat(allowFirstFinish.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    var executor = Executors.newFixedThreadPool(2);
    try {
      Future<MvcResult> first =
          executor.submit(() ->
              mvc.perform(
                      post("/api/opportunities/reassess")
                          .session(s.session())
                          .header("X-CSRF-Token", s.csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"criteriaVersion\":1}"))
                  .andReturn());

      assertThat(enteredFirst.await(5, TimeUnit.SECONDS)).isTrue();

      Future<MvcResult> second =
          executor.submit(() ->
              mvc.perform(
                      post("/api/opportunities/reassess")
                          .session(s.session())
                          .header("X-CSRF-Token", s.csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"criteriaVersion\":1}"))
                  .andReturn());

      MvcResult secondResult = second.get(5, TimeUnit.SECONDS);
      assertThat(secondResult.getResponse().getStatus()).isEqualTo(409);
      assertThat(
              JsonPath.<String>read(
                  secondResult.getResponse().getContentAsString(), "$.error.code"))
          .isEqualTo("reassessment_running");

      allowFirstFinish.countDown();
      MvcResult firstResult = first.get(5, TimeUnit.SECONDS);
      assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
      assertThat(JsonPath.<String>read(firstResult.getResponse().getContentAsString(), "$.state"))
          .isEqualTo("ready");
      assertThat(
              JsonPath.<Integer>read(
                  firstResult.getResponse().getContentAsString(), "$.remainingCount"))
          .isZero();
      mvc.perform(get("/api/opportunities/compatibility-status").session(s.session()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("ready"))
          .andExpect(jsonPath("$.stale").value(false));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void evidenceMutationMidCalculationReconcilesToLatestEvidenceRevision() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String opp =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "race-ev-1",
                "https://race-ev.test/1",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Race Co");
    // Mark pending by bumping revision without saving assessment.
    writer.run(() -> evidence.invalidate(opp));

    AtomicInteger reads = new AtomicInteger();
    CountDownLatch firstReadDone = new CountDownLatch(1);
    CountDownLatch evidenceUpdated = new CountDownLatch(1);
    snapshotHook.set(id -> {
      if (id.equals(opp) && reads.incrementAndGet() == 1) {
        firstReadDone.countDown();
        try {
          assertThat(evidenceUpdated.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<MvcResult> reassessFuture =
          executor.submit(() ->
              mvc.perform(
                      post("/api/opportunities/reassess")
                          .session(s.session())
                          .header("X-CSRF-Token", s.csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"criteriaVersion\":1}"))
                  .andReturn());

      assertThat(firstReadDone.await(5, TimeUnit.SECONDS)).isTrue();

      // Mutate evidence on a separate thread while pass 1 holds old snapshot.
      ingestion.ingest(
          new JobSource.Item(
              "board",
              "race-ev-1",
              "https://race-ev.test/1",
              "Requisitos: Python apenas.",
              "Desenvolvedor Backend Pleno",
              "Remoto",
              clock.localDate(),
              Completeness.complete,
              null),
          "Race Co");
      evidenceUpdated.countDown();

      MvcResult res = reassessFuture.get(5, TimeUnit.SECONDS);
      assertThat(res.getResponse().getStatus()).isEqualTo(200);
      assertThat(JsonPath.<String>read(res.getResponse().getContentAsString(), "$.state"))
          .isEqualTo("ready");

      var latest = assessments.find(opp).orElseThrow();
      assertThat(latest.evidenceRevision()).isEqualTo(evidence.revision(opp));
      assertThat(latest.technologyStatus()).isEqualTo(Axis.mismatched);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void discardAndMergeDuringCommitPreserveDecisionsAndNeverPersistMergedAssessment()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String discardOpp =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "disc-race",
                "https://race-dm.test/discard",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Discard Co");
    String mergeA =
        ingestion.ingest(
            new JobSource.Item(
                "board-a",
                "m-a",
                "https://race-dm.test/ma",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Merge Race Co");
    String mergeB =
        ingestion.ingest(
            new JobSource.Item(
                "board-b",
                "m-b",
                "https://race-dm.test/mb",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Merge Race Co");
    String survivor = mergeA.compareTo(mergeB) < 0 ? mergeA : mergeB;
    String merged = mergeA.compareTo(mergeB) < 0 ? mergeB : mergeA;
    writer.run(() -> dedupeRepository.suggest(survivor, merged, clock.now()));

    // Clear assessments so all 3 are pending for v1 reassess.
    writer.run(() -> em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate());

    CountDownLatch allSnapshotsRead = new CountDownLatch(1);
    CountDownLatch mutationsApplied = new CountDownLatch(1);
    AtomicInteger passOneCount = new AtomicInteger();
    Set<String> targets = Set.of(discardOpp, survivor, merged);
    snapshotHook.set(id -> {
      if (targets.contains(id) && passOneCount.incrementAndGet() == 3) {
        allSnapshotsRead.countDown();
        try {
          assertThat(mutationsApplied.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<MvcResult> reassessFuture =
          executor.submit(() ->
              mvc.perform(
                      post("/api/opportunities/reassess")
                          .session(s.session())
                          .header("X-CSRF-Token", s.csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"criteriaVersion\":1}"))
                  .andReturn());

      assertThat(allSnapshotsRead.await(5, TimeUnit.SECONDS)).isTrue();

      // Discard discardOpp and merge merged -> survivor before commitBatch executes!
      mvc.perform(
              post("/api/opportunities/" + discardOpp + "/discard")
                  .session(s.session())
                  .header("X-CSRF-Token", s.csrf()))
          .andExpect(status().isOk());
      mvc.perform(
              post("/api/dedupe-candidates/" + survivor + ":" + merged + "/merge")
                  .session(s.session())
                  .header("X-CSRF-Token", s.csrf()))
          .andExpect(status().isOk());

      mutationsApplied.countDown();

      MvcResult res = reassessFuture.get(5, TimeUnit.SECONDS);
      assertThat(res.getResponse().getStatus()).isEqualTo(200);
      assertThat(JsonPath.<String>read(res.getResponse().getContentAsString(), "$.state"))
          .isEqualTo("ready");
      assertThat(JsonPath.<Integer>read(res.getResponse().getContentAsString(), "$.remainingCount"))
          .isZero();

      // Discarded job stayed discarded (never leaked to selection/secondary) and merged job got NO
      // assessment.
      assertThat(jobs.get(discardOpp).userDecision()).isEqualTo("discarded");
      assertThat(jobs.get(merged).mergedInto()).isEqualTo(survivor);
      assertThat(assessments.find(merged)).isEmpty();
      assertThat(assessments.find(survivor).orElseThrow().evidenceRevision())
          .isEqualTo(evidence.revision(survivor));
      assertThat(assessments.find(discardOpp).orElseThrow().evidenceRevision())
          .isEqualTo(evidence.revision(discardOpp));
      mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(survivor))
          .andExpect(jsonPath("$.items[0].compatibility.current").value(true));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void staleWorkerCompletionCannotOverwriteNewCriteriaVersionOrNewRunOwner() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String opp =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "stale-worker",
                "https://race-stale.test/1",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Stale Co");
    writer.run(() ->
        em.createNativeQuery("DELETE FROM opportunity_compatibility WHERE opportunity_id = :id")
            .setParameter("id", opp)
            .executeUpdate());

    var snapV1 = inputs.snapshot(opp).orElseThrow();
    var staleReservation = refresh.reserve(1);
    assertThat(staleReservation.runId()).isNotBlank();

    // Advance criteria to v2 (pending, runId = null), then start a new v2 run (runB).
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
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));

    var newRun = refresh.reserve(2);
    assertThat(newRun.runId()).isNotBlank().isNotEqualTo(staleReservation.runId());

    // Old worker from v1 tries to save v1 assessment, report progress, complete, and fail.
    var obsoleteAssessment =
        new dev.curriculotron.opportunities.domain.CompatibilityAssessment(
            opp,
            snapV1.settings().version(),
            dev.curriculotron.opportunities.domain.CompatibilityAssessment.EVALUATOR_VERSION,
            snapV1.evidenceRevision(),
            clock.now(),
            Axis.matched,
            Axis.matched,
            List.of("role_match", "technology_match"),
            new dev.curriculotron.opportunities.domain.CompatibilityAssessment.CriteriaSnapshot(
                snapV1.settings().criteria(),
                snapV1.settings().cltMinimum(),
                snapV1.settings().pjMinimum()),
            List.of(),
            "strong",
            80,
            List.of("Stale v1"));

    assertThat(writer.write(() -> assessments.saveIfCurrent(obsoleteAssessment))).isFalse();
    assertThat(refresh.recordProgress(staleReservation.runId(), 1, 1, 1)).isFalse();
    assertThat(refresh.completeIfOwner(staleReservation.runId(), 1, 1)).isEmpty();
    assertThat(refresh.failIfOwner(staleReservation.runId(), 1, 1, "assessment_failed")).isFalse();

    // New runB remains owner in running state for v2, and no obsolete assessment was saved.
    assertThat(assessments.find(opp)).isEmpty();
    assertThat(refresh.isOwner(newRun.runId(), 2, 1)).isTrue();
    var status = refresh.status();
    assertThat(status.criteriaVersion()).isEqualTo(2);
    assertThat(status.state()).isEqualTo(State.running);
  }

  private void waitUntilSearchFinishes(String runId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      var run = tasks.find(runId);
      if (run.isPresent()
          && !run.get().state().name().equals("queued")
          && !run.get().state().name().equals("running")) {
        return;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("Search task did not finish in time: " + runId);
  }
}
