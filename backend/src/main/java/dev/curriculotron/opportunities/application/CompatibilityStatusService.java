package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import dev.curriculotron.sources.SearchCriteriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompatibilityStatusService {
  private final SearchCriteriaRepository criteria;
  private final CompatibilityRepository assessments;
  private final CompatibilityRefreshRepository refresh;

  public CompatibilityStatusService(
      SearchCriteriaRepository criteria,
      CompatibilityRepository assessments,
      CompatibilityRefreshRepository refresh) {
    this.criteria = criteria;
    this.assessments = assessments;
    this.refresh = refresh;
  }

  @Transactional(readOnly = true)
  public CompatibilityStatus current() {
    long version = criteria.current().version();
    var stored = refresh.current();
    long remaining = assessments.pendingCount(version, CompatibilityAssessment.EVALUATOR_VERSION);
    boolean wrongVersion =
        stored.criteriaVersion() != version
            || stored.evaluatorVersion() != CompatibilityAssessment.EVALUATOR_VERSION;
    State state =
        wrongVersion || (stored.state() == State.ready && remaining > 0)
            ? State.pending
            : stored.state();
    return new CompatibilityStatus(
        version,
        state,
        state != State.ready || remaining > 0,
        wrongVersion ? 0 : stored.assessedCount(),
        remaining,
        wrongVersion ? null : stored.startedAt(),
        wrongVersion ? null : stored.finishedAt(),
        wrongVersion ? null : stored.errorCode());
  }
}
