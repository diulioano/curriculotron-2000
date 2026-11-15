package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class CompatibilityRefreshPersistenceAdapter implements CompatibilityRefreshRepository {
  private final CompatibilityRefreshJpaRepository refresh;

  CompatibilityRefreshPersistenceAdapter(CompatibilityRefreshJpaRepository refresh) {
    this.refresh = refresh;
  }

  public CompatibilityRefresh current() {
    var r =
        refresh
            .findById(1)
            .orElseThrow(() -> new IllegalStateException("Refresh state is missing"));
    return new CompatibilityRefresh(
        r.targetCriteriaVersion,
        r.evaluatorVersion,
        r.runId,
        CompatibilityRefresh.State.valueOf(r.state),
        instant(r.startedAt),
        instant(r.finishedAt),
        r.errorCode,
        r.assessedCount,
        r.remainingCount);
  }

  public void save(CompatibilityRefresh value) {
    var r = new CompatibilityRefreshEntity();
    r.id = 1;
    r.targetCriteriaVersion = value.criteriaVersion();
    r.evaluatorVersion = value.evaluatorVersion();
    r.runId = value.runId();
    r.state = value.state().name();
    r.startedAt = value.startedAt() == null ? null : value.startedAt().toString();
    r.finishedAt = value.finishedAt() == null ? null : value.finishedAt().toString();
    r.errorCode = value.errorCode();
    r.assessedCount = value.assessedCount();
    r.remainingCount = value.remainingCount();
    refresh.saveAndFlush(r);
  }

  private static Instant instant(String value) {
    return value == null ? null : Instant.parse(value);
  }
}
