package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.opportunities.application.AssessOpportunityService;
import dev.curriculotron.opportunities.application.CompatibilityConflictException;
import dev.curriculotron.opportunities.application.CompatibilityConflictException.Reason;
import dev.curriculotron.opportunities.application.CompatibilityRefreshService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.CriteriaService;
import dev.curriculotron.sources.CriteriaVersionConflictException;
import dev.curriculotron.support.AppTestBase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CompatibilityRefreshStateTest extends AppTestBase {
  @Autowired CompatibilityRefreshService service;
  @Autowired CompatibilityRefreshRepository refreshRepository;
  @Autowired AssessOpportunityService assessService;
  @Autowired OpportunityRepository jobs;
  @Autowired EvidenceRepository evidence;
  @Autowired CriteriaService criteria;
  @Autowired WriteExecutor writer;
  @Autowired EntityManager em;
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
  void shouldTransitionPendingToRunningAndRequireZeroPendingBeforeReady() {
    String oppId =
        insertOpportunityWithEvidence("Backend Java", "Requisitos: Java 17 e Spring Boot");

    var initial = service.status();
    assertThat(initial.criteriaVersion()).isEqualTo(1);
    assertThat(initial.state()).isEqualTo(State.pending);
    assertThat(initial.stale()).isTrue();
    assertThat(initial.assessedCount()).isZero();
    assertThat(initial.remainingCount()).isEqualTo(1);

    var reservation = service.reserve(1);
    assertThat(reservation.noOp()).isFalse();
    assertThat(reservation.runId()).isNotBlank();
    assertThat(reservation.remainingCount()).isEqualTo(1);
    assertThat(reservation.status().state()).isEqualTo(State.running);
    assertThat(reservation.status().stale()).isTrue();
    assertThat(reservation.status().startedAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
    assertThat(service.isOwner(reservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isTrue();

    assertThat(
            service.completeIfOwner(
                reservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isEmpty();
    assertThat(service.status().state()).isEqualTo(State.running);

    assertThat(assessService.assessAndSave(oppId)).isTrue();
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:05:00Z"), AppClock.ZONE));
    assertThat(
            service.recordProgress(
                reservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION, 1))
        .isTrue();

    var runningAfterProgress = service.status();
    assertThat(runningAfterProgress.assessedCount()).isEqualTo(1);
    assertThat(runningAfterProgress.remainingCount()).isZero();
    assertThat(runningAfterProgress.state()).isEqualTo(State.running);

    var completed =
        service
            .completeIfOwner(reservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION)
            .orElseThrow();
    assertThat(completed.state()).isEqualTo(State.ready);
    assertThat(completed.stale()).isFalse();
    assertThat(completed.assessedCount()).isEqualTo(1);
    assertThat(completed.remainingCount()).isZero();
    assertThat(completed.startedAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
    assertThat(completed.finishedAt()).isEqualTo(Instant.parse("2026-11-15T12:05:00Z"));
    assertThat(completed.errorCode()).isNull();
    assertThat(refreshRepository.current().runId()).isNull();
  }

  @Test
  void shouldTreatReadyWithZeroPendingAsNoOpAndDeriveStaleWhenUnassessedOpportunityExists() {
    String assessedId = insertOpportunityWithEvidence("Backend Java", "Java 17 e REST");
    assertThat(assessService.assessAndSave(assessedId)).isTrue();

    var firstReservation = service.reserve(1);
    assertThat(
            service.recordProgress(
                firstReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION, 1))
        .isTrue();
    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:01:00Z"), AppClock.ZONE));
    var ready =
        service
            .completeIfOwner(firstReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION)
            .orElseThrow();
    assertThat(ready.stale()).isFalse();

    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:02:00Z"), AppClock.ZONE));
    var noOp = service.reserve(1);
    assertThat(noOp.noOp()).isTrue();
    assertThat(noOp.runId()).isNull();
    assertThat(noOp.status().state()).isEqualTo(State.ready);
    assertThat(noOp.status().assessedCount()).isEqualTo(1);
    assertThat(noOp.status().finishedAt()).isEqualTo(Instant.parse("2026-11-15T12:01:00Z"));

    String unassessedId = insertOpportunityWithEvidence("Java Engineer", "Spring Boot e Kafka");
    var derivedStale = service.status();
    assertThat(derivedStale.state()).isEqualTo(State.pending);
    assertThat(derivedStale.stale()).isTrue();
    assertThat(derivedStale.remainingCount()).isEqualTo(1);

    var secondReservation = service.reserve(1);
    assertThat(secondReservation.noOp()).isFalse();
    assertThat(secondReservation.runId()).isNotBlank();
    assertThat(secondReservation.remainingCount()).isEqualTo(1);
    assertThat(assessService.assessAndSave(unassessedId)).isTrue();
    assertThat(
            service.recordProgress(
                secondReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION, 1))
        .isTrue();
    assertThat(
            service.completeIfOwner(
                secondReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isPresent();
  }

  @Test
  void shouldRejectConcurrentReservationAndCriteriaVersionMismatch() {
    insertOpportunityWithEvidence("Backend Java", "Java");
    var active = service.reserve(1);
    assertThat(active.runId()).isNotBlank();

    assertThatThrownBy(() -> service.reserve(1))
        .isInstanceOf(CompatibilityConflictException.class)
        .satisfies(ex ->
            assertThat(((CompatibilityConflictException) ex).reason())
                .isEqualTo(Reason.REASSESSMENT_RUNNING));

    assertThatThrownBy(() -> service.reserve(99))
        .isInstanceOf(CompatibilityConflictException.class)
        .satisfies(ex ->
            assertThat(((CompatibilityConflictException) ex).reason())
                .isEqualTo(Reason.CRITERIA_VERSION_CONFLICT));

    assertThatThrownBy(() -> service.reserve(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.reserve(-3)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldInvalidatePreviousOwnerWhenCriteriaPutIncrementsVersion() {
    String oppId = insertOpportunityWithEvidence("Backend Python", "Python e Django");
    var oldReservation = service.reserve(1);

    var base = criteria.current();
    var updatedCriteria =
        new dev.curriculotron.sources.CriteriaDocument(
            base.version(),
            base.targetRoles(),
            List.of("Python", "Django"),
            base.remotePreferred(),
            base.hybridRegions(),
            base.cltMinMonthlyBrl(),
            base.pjMinMonthlyBrl(),
            base.acceptJuniorIfJustified(),
            base.dailySearchTimeLocal(),
            base.deadlineHighlightDays(),
            base.urgentHours(),
            base.emailDigestEnabled());
    var saved = criteria.save(updatedCriteria);
    assertThat(saved.version()).isEqualTo(2);

    assertThatThrownBy(() -> criteria.save(updatedCriteria))
        .isInstanceOf(CriteriaVersionConflictException.class);

    assertThat(
            service.isOwner(oldReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isFalse();
    assertThat(
            service.recordProgress(
                oldReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION, 1))
        .isFalse();
    assertThat(
            service.completeIfOwner(
                oldReservation.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isEmpty();
    assertThat(
            service.failIfOwner(
                oldReservation.runId(),
                1,
                CompatibilityAssessment.EVALUATOR_VERSION,
                "interrupted"))
        .isFalse();

    assertThat(
            writer.write(() ->
                refreshRepository.updateIfOwner(
                    oldReservation.runId(),
                    1,
                    CompatibilityAssessment.EVALUATOR_VERSION,
                    new CompatibilityRefresh(
                        1,
                        CompatibilityAssessment.EVALUATOR_VERSION,
                        null,
                        State.ready,
                        Instant.parse("2026-11-15T12:00:00Z"),
                        Instant.parse("2026-11-15T12:01:00Z"),
                        null,
                        1,
                        0))))
        .isFalse();

    var currentStatus = service.status();
    assertThat(currentStatus.criteriaVersion()).isEqualTo(2);
    assertThat(currentStatus.state()).isEqualTo(State.pending);
    assertThat(currentStatus.stale()).isTrue();
    assertThat(currentStatus.remainingCount()).isEqualTo(1);
    assertThat(refreshRepository.current().runId()).isNull();

    var newReservation = service.reserve(2);
    assertThat(assessService.assessAndSave(oppId)).isTrue();
    assertThat(
            service.recordProgress(
                newReservation.runId(), 2, CompatibilityAssessment.EVALUATOR_VERSION, 1))
        .isTrue();
    assertThat(
            service.completeIfOwner(
                newReservation.runId(), 2, CompatibilityAssessment.EVALUATOR_VERSION))
        .isPresent();
  }

  @Test
  void shouldSanitizeErrorCodesAndAllowRetryAfterFailure() {
    String oppId = insertOpportunityWithEvidence("Backend Java", "Java 17");
    var first = service.reserve(1);

    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:03:00Z"), AppClock.ZONE));
    assertThat(
            service.failIfOwner(
                first.runId(),
                1,
                CompatibilityAssessment.EVALUATOR_VERSION,
                "SQLITE_ERROR: path C:/secret/db"))
        .isTrue();

    var failed = service.status();
    assertThat(failed.state()).isEqualTo(State.failed);
    assertThat(failed.stale()).isTrue();
    assertThat(failed.errorCode()).isEqualTo("assessment_failed");
    assertThat(failed.finishedAt()).isEqualTo(Instant.parse("2026-11-15T12:03:00Z"));
    assertThat(failed.remainingCount()).isEqualTo(1);
    assertThat(service.isOwner(first.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION))
        .isFalse();

    clock.setClock(Clock.fixed(Instant.parse("2026-11-15T12:04:00Z"), AppClock.ZONE));
    var retry = service.reserve(1);
    assertThat(retry.runId()).isNotEqualTo(first.runId());
    assertThat(assessService.assessAndSave(oppId)).isTrue();
    assertThat(
            service.recordProgress(retry.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION, 1))
        .isTrue();
    var ready =
        service
            .completeIfOwner(retry.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION)
            .orElseThrow();
    assertThat(ready.state()).isEqualTo(State.ready);
    assertThat(ready.stale()).isFalse();
    assertThat(ready.errorCode()).isNull();
  }

  @Test
  void shouldPreserveAllowedSafeErrorCodesAndRejectWrongUpdateIfOwnerTarget() {
    insertOpportunityWithEvidence("Backend Java", "Java 17");
    var reservation = service.reserve(1);

    assertThat(
            writer.write(() ->
                refreshRepository.updateIfOwner(
                    "wrong-run-id",
                    1,
                    CompatibilityAssessment.EVALUATOR_VERSION,
                    new CompatibilityRefresh(
                        1,
                        CompatibilityAssessment.EVALUATOR_VERSION,
                        null,
                        State.failed,
                        reservation.startedAt(),
                        Instant.parse("2026-11-15T12:01:00Z"),
                        "interrupted",
                        0,
                        1))))
        .isFalse();

    assertThat(
            service.failIfOwner(
                reservation.runId(),
                1,
                CompatibilityAssessment.EVALUATOR_VERSION,
                "evidence_changed"))
        .isTrue();
    assertThat(service.status().errorCode()).isEqualTo("evidence_changed");

    var second = service.reserve(1);
    assertThat(
            service.failIfOwner(
                second.runId(), 1, CompatibilityAssessment.EVALUATOR_VERSION, "interrupted"))
        .isTrue();
    assertThat(service.status().errorCode()).isEqualTo("interrupted");
  }

  private String insertOpportunityWithEvidence(String title, String description) {
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
                VacancyEvidence.Completeness.complete,
                null,
                1,
                Instant.EPOCH,
                null)));
    return id;
  }
}
