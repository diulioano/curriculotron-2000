package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.CompatibilityConflictException.Reason;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompatibilityRefreshService {
  private static final Set<String> SAFE_ERROR_CODES =
      Set.of("assessment_failed", "interrupted", "evidence_changed");

  private final SearchCriteriaRepository criteria;
  private final CompatibilityRepository assessments;
  private final CompatibilityRefreshRepository refresh;
  private final CompatibilityStatusService statusService;
  private final WriteExecutor writer;
  private final AppClock clock;

  public CompatibilityRefreshService(
      SearchCriteriaRepository criteria,
      CompatibilityRepository assessments,
      CompatibilityRefreshRepository refresh,
      CompatibilityStatusService statusService,
      WriteExecutor writer,
      AppClock clock) {
    this.criteria = criteria;
    this.assessments = assessments;
    this.refresh = refresh;
    this.statusService = statusService;
    this.writer = writer;
    this.clock = clock;
  }

  public record Reservation(
      String runId,
      long criteriaVersion,
      int evaluatorVersion,
      Instant startedAt,
      long assessedCount,
      long remainingCount,
      boolean noOp,
      CompatibilityStatus status) {}

  @Transactional(readOnly = true)
  public CompatibilityStatus status() {
    return statusService.current();
  }

  public Reservation reserve(long requestedCriteriaVersion) {
    if (requestedCriteriaVersion <= 0) {
      throw new IllegalArgumentException("Criteria version must be positive");
    }
    return writer.write(() -> {
      long currentVersion = criteria.current().version();
      if (requestedCriteriaVersion != currentVersion) {
        throw new CompatibilityConflictException(Reason.CRITERIA_VERSION_CONFLICT);
      }
      int evaluatorVersion = CompatibilityAssessment.EVALUATOR_VERSION;
      var stored = refresh.current();
      long remaining = assessments.pendingCount(currentVersion, evaluatorVersion);
      boolean sameTarget =
          stored.criteriaVersion() == currentVersion
              && stored.evaluatorVersion() == evaluatorVersion;
      if (sameTarget && stored.state() == State.running) {
        throw new CompatibilityConflictException(Reason.REASSESSMENT_RUNNING);
      }
      if (sameTarget && stored.state() == State.ready && remaining == 0) {
        return new Reservation(
            null,
            currentVersion,
            evaluatorVersion,
            stored.startedAt(),
            stored.assessedCount(),
            0,
            true,
            statusService.current());
      }
      String runId = Ids.newId();
      Instant now = clock.now();
      refresh.save(
          new CompatibilityRefresh(
              currentVersion,
              evaluatorVersion,
              runId,
              State.running,
              now,
              null,
              null,
              0,
              remaining));
      return new Reservation(
          runId,
          currentVersion,
          evaluatorVersion,
          now,
          0,
          remaining,
          false,
          statusService.current());
    });
  }

  @Transactional(readOnly = true)
  public boolean isOwner(String runId, long criteriaVersion, int evaluatorVersion) {
    if (runId == null || runId.isBlank()) {
      return false;
    }
    if (criteria.current().version() != criteriaVersion
        || evaluatorVersion != CompatibilityAssessment.EVALUATOR_VERSION) {
      return false;
    }
    var stored = refresh.current();
    return stored.state() == State.running
        && Objects.equals(stored.runId(), runId)
        && stored.criteriaVersion() == criteriaVersion
        && stored.evaluatorVersion() == evaluatorVersion;
  }

  public boolean recordProgress(
      String runId, long criteriaVersion, int evaluatorVersion, long batchAssessed) {
    if (runId == null || runId.isBlank()) {
      return false;
    }
    if (batchAssessed < 0) {
      throw new IllegalArgumentException("Batch assessed count cannot be negative");
    }
    return writer.write(() -> {
      if (!isCurrentOwner(runId, criteriaVersion, evaluatorVersion)) {
        return false;
      }
      var stored = refresh.current();
      long remaining = assessments.pendingCount(criteriaVersion, evaluatorVersion);
      long assessed = stored.assessedCount() + batchAssessed;
      return refresh.updateIfOwner(
          runId,
          criteriaVersion,
          evaluatorVersion,
          new CompatibilityRefresh(
              criteriaVersion,
              evaluatorVersion,
              runId,
              State.running,
              stored.startedAt(),
              null,
              null,
              assessed,
              remaining));
    });
  }

  public Optional<CompatibilityStatus> completeIfOwner(
      String runId, long criteriaVersion, int evaluatorVersion) {
    if (runId == null || runId.isBlank()) {
      return Optional.empty();
    }
    return writer.write(() -> {
      if (!isCurrentOwner(runId, criteriaVersion, evaluatorVersion)) {
        return Optional.empty();
      }
      long remaining = assessments.pendingCount(criteriaVersion, evaluatorVersion);
      if (remaining != 0) {
        return Optional.empty();
      }
      var stored = refresh.current();
      Instant finishedAt = clock.now();
      boolean updated =
          refresh.updateIfOwner(
              runId,
              criteriaVersion,
              evaluatorVersion,
              new CompatibilityRefresh(
                  criteriaVersion,
                  evaluatorVersion,
                  null,
                  State.ready,
                  stored.startedAt(),
                  finishedAt,
                  null,
                  stored.assessedCount(),
                  0));
      if (!updated) {
        return Optional.empty();
      }
      return Optional.of(statusService.current());
    });
  }

  public boolean failIfOwner(
      String runId, long criteriaVersion, int evaluatorVersion, String errorCode) {
    if (runId == null || runId.isBlank()) {
      return false;
    }
    String safeCode = sanitizeErrorCode(errorCode);
    return writer.write(() -> {
      var stored = refresh.current();
      if (stored.state() != State.running
          || !Objects.equals(stored.runId(), runId)
          || stored.criteriaVersion() != criteriaVersion
          || stored.evaluatorVersion() != evaluatorVersion) {
        return false;
      }
      long remaining = assessments.pendingCount(criteriaVersion, evaluatorVersion);
      Instant finishedAt = clock.now();
      return refresh.updateIfOwner(
          runId,
          criteriaVersion,
          evaluatorVersion,
          new CompatibilityRefresh(
              criteriaVersion,
              evaluatorVersion,
              null,
              State.failed,
              stored.startedAt(),
              finishedAt,
              safeCode,
              stored.assessedCount(),
              remaining));
    });
  }

  private boolean isCurrentOwner(String runId, long criteriaVersion, int evaluatorVersion) {
    if (criteria.current().version() != criteriaVersion
        || evaluatorVersion != CompatibilityAssessment.EVALUATOR_VERSION) {
      return false;
    }
    var stored = refresh.current();
    return stored.state() == State.running
        && Objects.equals(stored.runId(), runId)
        && stored.criteriaVersion() == criteriaVersion
        && stored.evaluatorVersion() == evaluatorVersion;
  }

  private static String sanitizeErrorCode(String errorCode) {
    if (errorCode != null && SAFE_ERROR_CODES.contains(errorCode)) {
      return errorCode;
    }
    return "assessment_failed";
  }
}
