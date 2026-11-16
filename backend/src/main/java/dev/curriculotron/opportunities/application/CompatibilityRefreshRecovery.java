package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRefreshRepository;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh;
import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CompatibilityRefreshRecovery {
  private static final String INTERRUPTED_CODE = "interrupted";
  private static final Logger log = LoggerFactory.getLogger(CompatibilityRefreshRecovery.class);

  private final CompatibilityRefreshRepository refresh;
  private final CompatibilityRefreshService refreshService;

  public CompatibilityRefreshRecovery(
      CompatibilityRefreshRepository refresh, CompatibilityRefreshService refreshService) {
    this.refresh = refresh;
    this.refreshService = refreshService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    CompatibilityRefresh stored;
    try {
      stored = refresh.current();
    } catch (RuntimeException failure) {
      if (hasInterruptedCause(failure)) {
        Thread.currentThread().interrupt();
      }
      log.error(
          "Falha ao ler estado da reavaliacao na inicializacao errorCode={} exception={}",
          INTERRUPTED_CODE,
          failure.getClass().getSimpleName());
      return;
    }
    if (stored.state() != State.running || stored.runId() == null || stored.runId().isBlank()) {
      return;
    }
    try {
      refreshService.failIfOwner(
          stored.runId(), stored.criteriaVersion(), stored.evaluatorVersion(), INTERRUPTED_CODE);
    } catch (RuntimeException failure) {
      if (hasInterruptedCause(failure)) {
        Thread.currentThread().interrupt();
      }
      log.error(
          "Falha ao gravar interrupcao da reavaliacao runId={} errorCode={} exception={}",
          stored.runId(),
          INTERRUPTED_CODE,
          failure.getClass().getSimpleName());
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
