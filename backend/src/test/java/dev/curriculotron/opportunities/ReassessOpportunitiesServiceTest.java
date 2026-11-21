package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.AssessmentInputService;
import dev.curriculotron.opportunities.application.CompatibilityConflictException;
import dev.curriculotron.opportunities.application.CompatibilityConflictException.Reason;
import dev.curriculotron.opportunities.application.CompatibilityRefreshService;
import dev.curriculotron.opportunities.application.ReassessOpportunitiesService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.ConfiguredSourceRepository;
import dev.curriculotron.sources.CriteriaDocument;
import dev.curriculotron.sources.CriteriaService;
import dev.curriculotron.sources.SearchCriteriaRepository;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReassessOpportunitiesServiceTest extends AppTestBase {
  @Autowired ReassessOpportunitiesService service;
  @Autowired CompatibilityRefreshService refresh;
  @Autowired CompatibilityRepository assessments;
  @Autowired AssessmentInputService inputs;
  @Autowired AssessOpportunityService assessor;
  @Autowired OpportunityRepository jobs;
  @Autowired EvidenceRepository evidence;
  @Autowired OpportunityCorrectionRepository corrections;
  @Autowired SelectionRepository selections;
  @Autowired SearchCriteriaRepository criteriaRepo;
  @Autowired ConfiguredSourceRepository boards;
  @Autowired CriteriaService criteria;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
  @Autowired AppClock clock;

  @BeforeEach
  void reset() {
    Thread.interrupted();
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
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void shouldReassessLegacyPartialAndUpdatedCriteriaIgnoringMergedAndPreservingHistory() {
    String javaOpp =
        insertOpportunityWithEvidence(
            "Desenvolvedor Backend Pleno Java",
            "Requisitos: Java 17 e Spring Boot.",
            Completeness.complete);
    String pythonOpp =
        insertOpportunityWithEvidence(
            "Desenvolvedor Backend Pleno Python",
            "Requisitos: Python e FastAPI.",
            Completeness.complete);
    String legacyPartialOpp =
        insertOpportunityWithEvidence(
            "Desenvolvedor Backend Pleno", "Trabalho remoto.", Completeness.partial);
    String unassessedLegacyOpp =
        writer.write(
            () -> jobs.createManual("Vaga legada sem avaliação", Instant.EPOCH.toString()));
    String mergedOpp =
        insertOpportunityWithEvidence(
            "Backend Pleno Java Duplicada", "Requisitos: Java 17.", Completeness.complete);

    assertThat(assessor.assessAndSave(javaOpp)).isTrue();
    assertThat(assessor.assessAndSave(pythonOpp)).isTrue();
    assertThat(assessor.assessAndSave(legacyPartialOpp)).isTrue();

    LocalDate selectionDate = LocalDate.parse("2026-11-15");
    writer.run(() -> {
      jobs.setDecision(pythonOpp, "discarded", Instant.EPOCH.toString());
      em.createNativeQuery("UPDATE opportunity SET merged_into = :survivor WHERE id = :merged")
          .setParameter("survivor", javaOpp)
          .setParameter("merged", mergedOpp)
          .executeUpdate();
      selections.select(selectionDate, javaOpp, 1);
    });

    var current = criteria.current();
    var updated =
        criteria.save(
            new CriteriaDocument(
                current.version(),
                List.of("Desenvolvedor Backend Pleno", "Software Engineer"),
                List.of("Python", "FastAPI"),
                current.remotePreferred(),
                current.hybridRegions(),
                current.cltMinMonthlyBrl(),
                current.pjMinMonthlyBrl(),
                current.acceptJuniorIfJustified(),
                current.dailySearchTimeLocal(),
                current.deadlineHighlightDays(),
                current.urgentHours(),
                current.emailDigestEnabled()));
    assertThat(updated.version()).isEqualTo(2);

    var status = service.reassess(2);
    assertThat(status.criteriaVersion()).isEqualTo(2);
    assertThat(status.state()).isEqualTo(State.ready);
    assertThat(status.stale()).isFalse();
    assertThat(status.assessedCount()).isEqualTo(4);
    assertThat(status.remainingCount()).isZero();

    var javaAfter = assessments.find(javaOpp).orElseThrow();
    assertThat(javaAfter.criteriaVersion()).isEqualTo(2);
    assertThat(javaAfter.technologyStatus()).isEqualTo(Axis.mismatched);

    var pythonAfter = assessments.find(pythonOpp).orElseThrow();
    assertThat(pythonAfter.criteriaVersion()).isEqualTo(2);
    assertThat(pythonAfter.roleStatus()).isEqualTo(Axis.matched);
    assertThat(pythonAfter.technologyStatus()).isEqualTo(Axis.matched);
    assertThat(jobs.find(pythonOpp).orElseThrow().userDecision()).isEqualTo("discarded");

    var partialAfter = assessments.find(legacyPartialOpp).orElseThrow();
    assertThat(partialAfter.criteriaVersion()).isEqualTo(2);
    assertThat(partialAfter.roleStatus()).isEqualTo(Axis.matched);
    assertThat(partialAfter.technologyStatus()).isEqualTo(Axis.unknown);

    var legacyAfter = assessments.find(unassessedLegacyOpp).orElseThrow();
    assertThat(legacyAfter.criteriaVersion()).isEqualTo(2);
    assertThat(legacyAfter.technologyStatus()).isEqualTo(Axis.unknown);

    assertThat(assessments.find(mergedOpp)).isEmpty();
    assertThat(selections.selected(selectionDate)).containsExactly(javaOpp);
  }

  @Test
  void shouldCommitBatchesOfUpTo100AndRollbackFailingBatchCleanlyBeforeRetry() {
    for (int i = 0; i < 105; i++) {
      insertOpportunityWithEvidence(
          "Software Engineer Java " + i, "Requisitos: Java e Spring Boot.", Completeness.complete);
    }

    AtomicInteger saveCalls = new AtomicInteger();
    CompatibilityRepository failingOnSecondBatch =
        new CompatibilityRepository() {
          @Override
          public Optional<CompatibilityAssessment> find(String opportunityId) {
            return assessments.find(opportunityId);
          }

          @Override
          public boolean saveIfCurrent(CompatibilityAssessment assessment) {
            if (saveCalls.incrementAndGet() == 102) {
              throw new IllegalStateException("C:/private/sqlite/failure");
            }
            return assessments.saveIfCurrent(assessment);
          }

          @Override
          public List<String> pending(
              long criteriaVersion, int evaluatorVersion, String afterId, int limit) {
            return assessments.pending(criteriaVersion, evaluatorVersion, afterId, limit);
          }

          @Override
          public long pendingCount(long criteriaVersion, int evaluatorVersion) {
            return assessments.pendingCount(criteriaVersion, evaluatorVersion);
          }
        };

    var underTest =
        new ReassessOpportunitiesService(refresh, failingOnSecondBatch, inputs, assessor, writer);

    assertThatThrownBy(() -> underTest.reassess(1)).isInstanceOf(IllegalStateException.class);

    var failed = refresh.status();
    assertThat(failed.state()).isEqualTo(State.failed);
    assertThat(failed.stale()).isTrue();
    assertThat(failed.errorCode()).isEqualTo("assessment_failed");
    assertThat(failed.assessedCount()).isEqualTo(100);
    assertThat(failed.remainingCount()).isEqualTo(5);

    var retried = underTest.reassess(1);
    assertThat(retried.state()).isEqualTo(State.ready);
    assertThat(retried.stale()).isFalse();
    assertThat(retried.assessedCount()).isEqualTo(5);
    assertThat(retried.remainingCount()).isZero();
    assertThat(assessments.pendingCount(1, CompatibilityAssessment.EVALUATOR_VERSION)).isZero();
  }

  @Test
  void shouldReconcileConcurrentEvidenceChangeWithinThreePassesWithoutStaleOverwrite() {
    String volatileOpp =
        insertOpportunityWithEvidence(
            "Desenvolvedor Backend Pleno",
            "Requisitos: Java 17 e Spring Boot.",
            Completeness.complete);
    String stableOpp =
        insertOpportunityWithEvidence(
            "Software Engineer Java", "Requisitos: Java e Kafka.", Completeness.complete);

    AtomicInteger snapshotReads = new AtomicInteger();
    AssessmentInputService mutatingInputs =
        new AssessmentInputService(jobs, evidence, corrections, criteriaRepo, boards) {
          @Override
          public Optional<Input> snapshot(String id) {
            Optional<Input> snap = super.snapshot(id);
            if (id.equals(volatileOpp) && snapshotReads.incrementAndGet() == 1) {
              writer.run(() ->
                  evidence.upsert(
                      new VacancyEvidence(
                          "manual-" + volatileOpp,
                          volatileOpp,
                          null,
                          "manual",
                          "Desenvolvedor Backend Pleno",
                          null,
                          "Requisitos obrigatórios: Python e Django.",
                          Completeness.complete,
                          null,
                          1,
                          Instant.EPOCH,
                          null)));
            }
            return snap;
          }
        };

    var underTest =
        new ReassessOpportunitiesService(refresh, assessments, mutatingInputs, assessor, writer);

    var status = underTest.reassess(1);
    assertThat(status.state()).isEqualTo(State.ready);
    assertThat(status.stale()).isFalse();
    assertThat(status.remainingCount()).isZero();
    assertThat(snapshotReads.get()).isEqualTo(2);

    var latest = assessments.find(volatileOpp).orElseThrow();
    assertThat(latest.evidenceRevision()).isEqualTo(evidence.revision(volatileOpp));
    assertThat(latest.technologyStatus()).isEqualTo(Axis.mismatched);
    assertThat(assessments.find(stableOpp).orElseThrow().technologyStatus())
        .isEqualTo(Axis.matched);
  }

  @Test
  void shouldStopAtThreeReconciliationPassesAndFailWithEvidenceChanged() {
    String unstableOpp =
        insertOpportunityWithEvidence(
            "Backend Pleno Java", "Requisitos: Java 17.", Completeness.complete);
    String stableOpp =
        insertOpportunityWithEvidence(
            "Software Engineer Java", "Requisitos: Java e REST.", Completeness.complete);

    AtomicInteger snapshotReads = new AtomicInteger();
    AssessmentInputService alwaysMutatingInputs =
        new AssessmentInputService(jobs, evidence, corrections, criteriaRepo, boards) {
          @Override
          public Optional<Input> snapshot(String id) {
            Optional<Input> snap = super.snapshot(id);
            if (id.equals(unstableOpp) && snapshotReads.incrementAndGet() <= 3) {
              writer.run(() -> evidence.invalidate(unstableOpp));
            }
            return snap;
          }
        };

    var underTest =
        new ReassessOpportunitiesService(
            refresh, assessments, alwaysMutatingInputs, assessor, writer);

    assertThatThrownBy(() -> underTest.reassess(1))
        .isInstanceOf(CompatibilityConflictException.class)
        .satisfies(ex ->
            assertThat(((CompatibilityConflictException) ex).reason())
                .isEqualTo(Reason.EVIDENCE_CHANGED));

    assertThat(snapshotReads.get()).isEqualTo(3);
    var failed = refresh.status();
    assertThat(failed.state()).isEqualTo(State.failed);
    assertThat(failed.errorCode()).isEqualTo("evidence_changed");
    assertThat(failed.assessedCount()).isEqualTo(1);
    assertThat(failed.remainingCount()).isEqualTo(1);
    assertThat(assessments.find(stableOpp)).isPresent();
    assertThat(assessments.find(unstableOpp)).isEmpty();

    var retried = underTest.reassess(1);
    assertThat(retried.state()).isEqualTo(State.ready);
    assertThat(retried.assessedCount()).isEqualTo(1);
    assertThat(retried.remainingCount()).isZero();
  }

  @Test
  void shouldAbortWithoutOverwritingPendingWhenCriteriaChangesMidRun() {
    insertOpportunityWithEvidence("Backend Pleno Java", "Java 17.", Completeness.complete);

    AtomicInteger reads = new AtomicInteger();
    AssessmentInputService criteriaChangingInputs =
        new AssessmentInputService(jobs, evidence, corrections, criteriaRepo, boards) {
          @Override
          public Optional<Input> snapshot(String id) {
            Optional<Input> snap = super.snapshot(id);
            if (reads.incrementAndGet() == 1) {
              var current = criteria.current();
              criteria.save(
                  new CriteriaDocument(
                      current.version(),
                      current.targetRoles(),
                      List.of("Python"),
                      current.remotePreferred(),
                      current.hybridRegions(),
                      current.cltMinMonthlyBrl(),
                      current.pjMinMonthlyBrl(),
                      current.acceptJuniorIfJustified(),
                      current.dailySearchTimeLocal(),
                      current.deadlineHighlightDays(),
                      current.urgentHours(),
                      current.emailDigestEnabled()));
            }
            return snap;
          }
        };

    var underTest =
        new ReassessOpportunitiesService(
            refresh, assessments, criteriaChangingInputs, assessor, writer);

    assertThatThrownBy(() -> underTest.reassess(1))
        .isInstanceOf(CompatibilityConflictException.class)
        .satisfies(ex ->
            assertThat(((CompatibilityConflictException) ex).reason())
                .isEqualTo(Reason.CRITERIA_VERSION_CONFLICT));

    var status = refresh.status();
    assertThat(status.criteriaVersion()).isEqualTo(2);
    assertThat(status.state()).isEqualTo(State.pending);
    assertThat(status.stale()).isTrue();
    assertThat(status.remainingCount()).isEqualTo(1);
  }

  @Test
  void shouldRecordInterruptedErrorCodeAndPreserveInterruptSignal() {
    insertOpportunityWithEvidence("Backend Pleno Java", "Java 17.", Completeness.complete);

    AssessmentInputService interruptingInputs =
        new AssessmentInputService(jobs, evidence, corrections, criteriaRepo, boards) {
          @Override
          public Optional<Input> snapshot(String id) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", new InterruptedException("stop"));
          }
        };

    var underTest =
        new ReassessOpportunitiesService(
            refresh, assessments, interruptingInputs, assessor, writer);

    assertThatThrownBy(() -> underTest.reassess(1)).isInstanceOf(IllegalStateException.class);
    assertThat(Thread.interrupted()).isTrue();

    var failed = refresh.status();
    assertThat(failed.state()).isEqualTo(State.failed);
    assertThat(failed.errorCode()).isEqualTo("interrupted");
    assertThat(failed.remainingCount()).isEqualTo(1);
  }

  private String insertOpportunityWithEvidence(
      String title, String description, Completeness completeness) {
    String id = writer.write(() -> jobs.createManual(title, Instant.EPOCH.toString()));
    writer.run(() ->
        evidence.upsert(
            new VacancyEvidence(
                "manual-" + id,
                id,
                null,
                "manual",
                title,
                null,
                description,
                completeness,
                null,
                1,
                Instant.EPOCH,
                null)));
    return id;
  }
}
