package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.CompatibilityConflictException.Reason;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReassessOpportunitiesService {
  static final int BATCH_SIZE = 100;
  static final int MAX_RECONCILIATION_PASSES = 3;

  private static final Logger log = LoggerFactory.getLogger(ReassessOpportunitiesService.class);

  private final CompatibilityRefreshService refresh;
  private final CompatibilityRepository assessments;
  private final AssessmentInputService inputs;
  private final AssessOpportunityService assessor;
  private final WriteExecutor writer;

  public ReassessOpportunitiesService(
      CompatibilityRefreshService refresh,
      CompatibilityRepository assessments,
      AssessmentInputService inputs,
      AssessOpportunityService assessor,
      WriteExecutor writer) {
    this.refresh = refresh;
    this.assessments = assessments;
    this.inputs = inputs;
    this.assessor = assessor;
    this.writer = writer;
  }

  public CompatibilityStatus reassess(long requestedCriteriaVersion) {
    var reservation = refresh.reserve(requestedCriteriaVersion);
    if (reservation.noOp()) {
      return reservation.status();
    }
    String runId = reservation.runId();
    long criteriaVersion = reservation.criteriaVersion();
    int evaluatorVersion = reservation.evaluatorVersion();

    try {
      for (int pass = 0; pass < MAX_RECONCILIATION_PASSES; pass++) {
        reconcilePass(runId, criteriaVersion, evaluatorVersion);
        Optional<CompatibilityStatus> completed =
            refresh.completeIfOwner(runId, criteriaVersion, evaluatorVersion);
        if (completed.isPresent()) {
          return completed.get();
        }
        if (!refresh.isOwner(runId, criteriaVersion, evaluatorVersion)) {
          throw new CompatibilityConflictException(Reason.CRITERIA_VERSION_CONFLICT);
        }
      }
      if (!refresh.isOwner(runId, criteriaVersion, evaluatorVersion)) {
        throw new CompatibilityConflictException(Reason.CRITERIA_VERSION_CONFLICT);
      }
      safeFailIfOwner(runId, criteriaVersion, evaluatorVersion, "evidence_changed");
      throw new CompatibilityConflictException(Reason.EVIDENCE_CHANGED);
    } catch (CompatibilityConflictException conflict) {
      throw conflict;
    } catch (RuntimeException failure) {
      boolean interrupted = Thread.interrupted() || hasInterruptedCause(failure);
      String errorCode = interrupted ? "interrupted" : "assessment_failed";
      try {
        safeFailIfOwner(runId, criteriaVersion, evaluatorVersion, errorCode);
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
      throw failure;
    }
  }

  private void reconcilePass(String runId, long criteriaVersion, int evaluatorVersion) {
    String afterId = "";
    while (true) {
      if (!refresh.isOwner(runId, criteriaVersion, evaluatorVersion)) {
        throw new CompatibilityConflictException(Reason.CRITERIA_VERSION_CONFLICT);
      }
      List<String> page =
          assessments.pending(criteriaVersion, evaluatorVersion, afterId, BATCH_SIZE);
      if (page.isEmpty()) {
        break;
      }
      List<CompatibilityAssessment> batch = new ArrayList<>(page.size());
      for (String opportunityId : page) {
        var snapshot = inputs.snapshot(opportunityId);
        if (snapshot.isEmpty()) {
          continue;
        }
        if (snapshot.get().settings().version() != criteriaVersion) {
          throw new CompatibilityConflictException(Reason.CRITERIA_VERSION_CONFLICT);
        }
        batch.add(assessor.calculate(snapshot.get()));
      }
      boolean stillOwner = commitBatch(runId, criteriaVersion, evaluatorVersion, batch);
      if (!stillOwner) {
        throw new CompatibilityConflictException(Reason.CRITERIA_VERSION_CONFLICT);
      }
      afterId = page.getLast();
      if (page.size() < BATCH_SIZE) {
        break;
      }
    }
  }

  private boolean commitBatch(
      String runId,
      long criteriaVersion,
      int evaluatorVersion,
      List<CompatibilityAssessment> batch) {
    return writer.write(() -> {
      if (!refresh.isOwner(runId, criteriaVersion, evaluatorVersion)) {
        return false;
      }
      long saved = 0;
      for (CompatibilityAssessment assessment : batch) {
        if (assessments.saveIfCurrent(assessment)) {
          saved++;
        }
      }
      return refresh.recordProgress(runId, criteriaVersion, evaluatorVersion, saved);
    });
  }

  private void safeFailIfOwner(
      String runId, long criteriaVersion, int evaluatorVersion, String errorCode) {
    try {
      refresh.failIfOwner(runId, criteriaVersion, evaluatorVersion, errorCode);
    } catch (RuntimeException persistenceFailure) {
      log.error(
          "Falha ao gravar estado de erro da reavaliacao runId={} errorCode={} exception={}",
          runId,
          errorCode,
          persistenceFailure.getClass().getSimpleName());
    }
  }

  private static boolean hasInterruptedCause(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof InterruptedException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
