package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.applications.ApplicationService;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.DedupeService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.opportunities.application.OpportunityService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.DedupeRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class CompatibilityEvidenceLifecycleTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired OpportunityService jobs;
  @Autowired DailySelectionService selections;
  @Autowired SelectionRepository selectionRepository;
  @Autowired OpportunityRepository opportunityRepository;
  @Autowired EvidenceRepository evidence;
  @Autowired CompatibilityRepository assessments;
  @Autowired MatchCandidateRepository candidates;
  @Autowired DedupeRepository dedupeRepository;
  @Autowired DedupeService dedupe;
  @Autowired ApplicationService applications;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
    writer.run(() -> {
      em.createNativeQuery("DELETE FROM daily_selection").executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_compatibility").executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_evidence WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_url_identity WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_external_identity WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity_source WHERE opportunity_id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery("DELETE FROM opportunity_match_candidate").executeUpdate();
      em.createNativeQuery(
              "UPDATE opportunity SET merged_into = NULL WHERE id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
      em.createNativeQuery(
              "DELETE FROM opportunity WHERE id NOT IN (SELECT opportunity_id FROM application)")
          .executeUpdate();
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
  void restoreInvalidatesRevisionAndReassessesWithoutErasingSelectionRankOrApplication()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "restore-1",
                "https://jobs.lever.co/ciandt/restore-1",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    selections.selectToday();
    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(id);

    mvc.perform(
            post("/api/opportunities/" + id + "/discard")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    // Change criteria from Java -> Python without running batch reassess.
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
        .andExpect(status().isOk());

    long revBeforeRestore = evidence.revision(id);

    mvc.perform(
            post("/api/opportunities/" + id + "/restore")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    assertThat(evidence.revision(id)).isGreaterThan(revBeforeRestore);
    var reassessed = assessments.find(id).orElseThrow();
    assertThat(reassessed.criteriaVersion()).isEqualTo(2);
    assertThat(reassessed.evidenceRevision()).isEqualTo(evidence.revision(id));
    assertThat(reassessed.technologyStatus()).isEqualTo(Axis.mismatched);
    assertThat(jobs.get(id).userDecision()).isEqualTo("none");
    // Daily selection history / rank 1 is preserved even though the vacancy now mismatches Python.
    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(id);
    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());

    // Change criteria back to Java -> restore again re-evaluates to matched and keeps rank 1.
    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":2,"targetRoles":["Desenvolvedor Backend Pleno"],
                     "stackKeywords":["Java"],"remotePreferred":true,"hybridRegions":[],
                     "cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,"acceptJuniorIfJustified":true,
                     "dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,"urgentHours":48,
                     "emailDigestEnabled":false}
                    """))
        .andExpect(status().isOk());

    long revBeforeSecondRestore = evidence.revision(id);
    mvc.perform(
            post("/api/opportunities/" + id + "/restore")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    assertThat(evidence.revision(id)).isGreaterThan(revBeforeSecondRestore);
    assertThat(assessments.find(id).orElseThrow().technologyStatus()).isEqualTo(Axis.matched);
    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].compatibility.current").value(true));

    // Applied vacancy must reject restore with 409 without erasing application or bumping revision.
    var app = applications.apply(id);
    long revApplied = evidence.revision(id);
    mvc.perform(
            post("/api/opportunities/" + id + "/restore")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isConflict());
    assertThat(evidence.revision(id)).isEqualTo(revApplied);
    assertThat(applications.detail(app.id()).opportunityId()).isEqualTo(id);
    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(id);
  }

  @Test
  void mergeInvalidatesAndReassessesEvenWithoutChildEvidenceWhilePreservingDecisionAndRank()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String first =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "m1",
                "https://merge-lifecycle.test/1",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Merge Co");
    String second =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                "m2",
                "https://merge-lifecycle.test/2",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Merge Co");

    var candidate = candidates.open().getFirst();
    String survivor = candidate.opportunityA();
    String merged = candidate.opportunityB();

    selections.selectToday();
    jobs.decide(merged, "discarded");

    // Simulate merged child having 0 rows in opportunity_evidence (e.g. incomplete legacy row).
    writer.run(() ->
        em.createNativeQuery("DELETE FROM opportunity_evidence WHERE opportunity_id = :id")
            .setParameter("id", merged)
            .executeUpdate());

    long revBeforeMerge = evidence.revision(survivor);
    List<String> selectedBefore = selectionRepository.selected(clock.localDate());

    mvc.perform(
            post("/api/dedupe-candidates/" + survivor + ":" + merged + "/merge")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    assertThat(evidence.revision(survivor)).isGreaterThan(revBeforeMerge);
    assertThat(jobs.get(survivor).userDecision()).isEqualTo("discarded");
    assertThat(jobs.get(merged).mergedInto()).isEqualTo(survivor);
    assertThat(assessments.find(survivor).orElseThrow().evidenceRevision())
        .isEqualTo(evidence.revision(survivor));
    assertThat(selectionRepository.selected(clock.localDate())).isEqualTo(selectedBefore);
  }

  @Test
  void mergeWithContradictorySourcesResolvesConflictAsUnknownAndExcludesFromSelection()
      throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    String first =
        ingestion.ingest(
            new JobSource.Item(
                "board-a",
                "conflict-1",
                "https://conflict-merge.test/1",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Conflict Co");
    String second =
        ingestion.ingest(
            new JobSource.Item(
                "board-b",
                "conflict-2",
                "https://conflict-merge.test/2",
                "Requisitos: Python. Não utilizamos Java neste time.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Conflict Co");

    selections.selectToday();
    var candidate = candidates.open().getFirst();
    String survivor = candidate.opportunityA();
    String merged = candidate.opportunityB();

    mvc.perform(
            post("/api/dedupe-candidates/" + survivor + ":" + merged + "/merge")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());

    assertThat(evidence.forOpportunity(survivor)).hasSize(2);
    var updated = assessments.find(survivor).orElseThrow();
    assertThat(updated.evidenceRevision()).isEqualTo(evidence.revision(survivor));
    assertThat(updated.technologyStatus()).isEqualTo(Axis.unknown);
    assertThat(updated.reasonCodes()).contains("technology_conflict");
    // Conflicting job must not stay in primary selection, while daily_selection history stays
    // intact.
    assertThat(selectionRepository.selected(clock.localDate())).isNotEmpty();
    mvc.perform(get("/api/opportunities?list=selection").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/opportunities/" + survivor).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compatibility.current").value(true))
        .andExpect(jsonPath("$.compatibility.technologyStatus").value("unknown"))
        .andExpect(
            jsonPath(
                "$.compatibility.reasonCodes",
                org.hamcrest.Matchers.hasItem("technology_conflict")));
  }

  @Test
  void refetchUpdatesRelevantFactsAndReassessesWithoutDuplicatesOrLosingDecisionAndApplication() {
    String id =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "refetch-1",
                "https://jobs.lever.co/ciandt/refetch-1",
                "Requisitos: Java e Spring Boot.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");
    selections.selectToday();
    var app = applications.apply(id);
    long revInitial = evidence.revision(id);
    long contentRevInitial = evidence.forOpportunity(id).getFirst().contentRevision();

    // Refetch same external identity with changed requirements, modality and facts.
    String sameId =
        ingestion.ingest(
            new JobSource.Item(
                "lever:ciandt",
                "refetch-1",
                "https://jobs.lever.co/ciandt/refetch-1",
                "Requisitos: Python e Django. Trabalho presencial.",
                "Desenvolvedor Backend Pleno",
                "Curitiba",
                clock.localDate(),
                Completeness.complete,
                null),
            "CI&T");

    assertThat(sameId).isEqualTo(id);
    assertThat(evidence.revision(id)).isGreaterThan(revInitial);
    assertThat(evidence.forOpportunity(id)).hasSize(1);
    assertThat(evidence.forOpportunity(id).getFirst().contentRevision())
        .isGreaterThan(contentRevInitial);
    var reloaded = jobs.get(id);
    assertThat(reloaded.userDecision()).isEqualTo("applied");
    assertThat(reloaded.workMode()).isEqualTo("onsite");
    assertThat(reloaded.location()).isEqualTo("Curitiba");
    assertThat(reloaded.facts()).anyMatch(f -> f.contains("Curitiba"));
    assertThat(reloaded.facts()).noneMatch(f -> f.contains("Menciona Java"));
    assertThat(applications.detail(app.id()).opportunityId()).isEqualTo(id);
    assertThat(selectionRepository.selected(clock.localDate())).containsExactly(id);
    var reassessed = assessments.find(id).orElseThrow();
    assertThat(reassessed.evidenceRevision()).isEqualTo(evidence.revision(id));
    assertThat(reassessed.technologyStatus()).isEqualTo(Axis.mismatched);
  }

  @Test
  void dedupeAttachingNewAliasOrSourceToExistingOpportunityInvalidatesRevisionInSameWrite() {
    String key = Ids.newId();
    String id =
        ingestion.ingest(
            new JobSource.Item(
                "board",
                key,
                "https://dedupe-rev.test/" + key,
                "Requisitos: Java",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Alias Co");
    long revBefore = evidence.revision(id);

    String attached =
        dedupe.ingest(
            "board",
            key,
            "https://dedupe-rev.test/alias/" + key,
            "new alias",
            () -> {
              throw new AssertionError("Should reuse existing opportunity");
            });

    assertThat(attached).isEqualTo(id);
    assertThat(evidence.revision(id)).isGreaterThan(revBefore);
  }

  @Test
  void multiSourcePartialConfirmedAndRoleConflictEvaluateAccurately() throws Exception {
    // 1. Board partial/unknown + manual complete Java -> matched on both axes.
    String confirmedId =
        ingestion.ingest(
            new JobSource.Item(
                "gupy:db",
                "partial-confirm",
                "https://multi.test/confirm",
                "Atuação em times ágeis.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.partial,
                "listing_summary"),
            "DB");
    assertThat(assessments.find(confirmedId).orElseThrow().technologyStatus())
        .isEqualTo(Axis.unknown);

    ingestion.ingest(
        new JobSource.Item(
            "manual",
            null,
            "https://multi.test/confirm",
            "Requisitos:\nJava\nSpring Boot",
            null,
            null,
            null,
            Completeness.complete,
            null),
        "Empresa a confirmar");

    var confirmed = assessments.find(confirmedId).orElseThrow();
    assertThat(confirmed.roleStatus()).isEqualTo(Axis.matched);
    assertThat(confirmed.technologyStatus()).isEqualTo(Axis.matched);
    assertThat(confirmed.reasonCodes()).contains("role_match", "technology_match");

    // 2. Contradictory role across two merged sources -> role_conflict (unknown).
    String r1 =
        ingestion.ingest(
            new JobSource.Item(
                "board-1",
                "rc-1",
                "https://role-conflict.test/1",
                "Requisitos: Java.",
                "Desenvolvedor Backend Pleno",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Role Co");
    String r2 =
        ingestion.ingest(
            new JobSource.Item(
                "board-2",
                "rc-2",
                "https://role-conflict.test/2",
                "Requisitos: Java.",
                "Service Designer",
                "Remoto",
                clock.localDate(),
                Completeness.complete,
                null),
            "Role Co");
    writer.run(() -> dedupeRepository.suggest(r1, r2, clock.now()));
    String survivor = r1.compareTo(r2) < 0 ? r1 : r2;
    String merged = r1.compareTo(r2) < 0 ? r2 : r1;
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    mvc.perform(
            post("/api/dedupe-candidates/" + survivor + ":" + merged + "/merge")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());
    var roleConflict = assessments.find(survivor).orElseThrow();
    assertThat(roleConflict.roleStatus()).isEqualTo(Axis.unknown);
    assertThat(roleConflict.reasonCodes()).contains("role_conflict");
  }
}
