package dev.curriculotron.sources;

import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.opportunities.application.CompatibilityStatusService;
import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.List;
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

  public List<String> priorityCompanies() {
    return store.priorityCompanies();
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
      return publish(requested.withVersion(current.version() + 1));
    });
  }

  /**
   * Alinha cargo e tecnologias ao perfil de carreira (US006) e sempre publica nova versão: o perfil
   * também muda senioridade e pisos, que entram na avaliação identificada por esta versão.
   */
  public CriteriaDocument alignWithProfile(
      List<String> targetRoles, List<String> stackKeywords, List<String> priorityCompanies) {
    return writer.write(() -> {
      var current = store.document();
      store.replacePriorityCompanies(priorityCompanies, clock.now());
      return publish(
          new CriteriaDocument(
              current.version() + 1,
              targetRoles,
              stackKeywords,
              current.remotePreferred(),
              current.hybridRegions(),
              current.cltMinMonthlyBrl(),
              current.pjMinMonthlyBrl(),
              current.acceptJuniorIfJustified(),
              current.dailySearchTimeLocal(),
              current.deadlineHighlightDays(),
              current.urgentHours(),
              current.emailDigestEnabled()));
    });
  }

  private CriteriaDocument publish(CriteriaDocument updated) {
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
  }
}
