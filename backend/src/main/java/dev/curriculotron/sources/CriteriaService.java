package dev.curriculotron.sources;

import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.opportunities.application.CompatibilityStatusService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriteriaService {
  private final CriteriaStore store;
  private final CompatibilityRefreshRepository refresh;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final CompatibilityStatusService status;

  public CriteriaService(
      CriteriaStore store,
      CompatibilityRefreshRepository refresh,
      WriteExecutor writer,
      AppClock clock,
      CompatibilityStatusService status) {
    this.store = store;
    this.refresh = refresh;
    this.writer = writer;
    this.clock = clock;
    this.status = status;
  }

  public record View(CriteriaDocument criteria, CompatibilityStatus assessment) {}

  @Transactional(readOnly = true)
  public View view() {
    return new View(store.document(), status.current());
  }

  public View saveView(CriteriaDocument requested) {
    return writer.write(() -> new View(save(requested), status.current()));
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
