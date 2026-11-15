package dev.curriculotron.sources;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import org.springframework.stereotype.Service;

@Service
public final class CriteriaService {
  private final CriteriaStore store;
  private final CompatibilityRefreshRepository refresh;
  private final WriteExecutor writer;
  private final AppClock clock;

  public CriteriaService(
      CriteriaStore store,
      CompatibilityRefreshRepository refresh,
      WriteExecutor writer,
      AppClock clock) {
    this.store = store;
    this.refresh = refresh;
    this.writer = writer;
    this.clock = clock;
  }

  public CriteriaDocument current() {
    return store.document();
  }

  public CriteriaDocument save(CriteriaDocument requested) {
    return writer.write(() -> {
      var current = store.document();
      if (current.version() != requested.version()) throw new CriteriaVersionConflictException();
      if (current.equals(requested)) return current;
      var updated = requested.withVersion(current.version() + 1);
      store.save(updated, clock.now());
      refresh.save(
          new CompatibilityRefresh(
              updated.version(),
              CompatibilityAssessment.EVALUATOR_VERSION,
              null,
              CompatibilityRefresh.State.pending,
              null,
              null,
              null,
              0,
              0));
      return updated;
    });
  }
}
