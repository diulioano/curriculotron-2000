package dev.curriculotron.audit.application;

import dev.curriculotron.audit.application.port.out.IntegrationStatusRepository;
import dev.curriculotron.audit.domain.IntegrationState;
import dev.curriculotron.audit.domain.IntegrationStatus;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

/** Casos de uso do estado das integrações (FR-017). */
@Service
public class IntegrationStatusService {

  private final IntegrationStatusRepository repository;
  private final WriteExecutor writer;
  private final AppClock clock;

  public IntegrationStatusService(
      IntegrationStatusRepository repository, WriteExecutor writer, AppClock clock) {
    this.repository = repository;
    this.writer = writer;
    this.clock = clock;
  }

  public void recordSuccess(String integration, String detail) {
    Instant now = clock.now();
    upsert(integration, s -> s.succeeded(now, detail));
  }

  public void recordFailure(String integration, IntegrationState state, String detail) {
    IntegrationStatus.requireFailureState(state);
    Instant now = clock.now();
    upsert(integration, s -> s.failed(now, state, detail));
  }

  public void setState(String integration, IntegrationState state, String detail) {
    upsert(integration, s -> s.withState(state, detail));
  }

  /** Só altera integrações já registradas. */
  public void setTrackingStartedAt(String integration, Instant at) {
    writer.run(
        () -> repository.find(integration).ifPresent(s -> repository.save(s.trackingSince(at))));
  }

  public Optional<IntegrationStatus> find(String integration) {
    return repository.find(integration);
  }

  public List<IntegrationStatus> findAll() {
    return repository.findAll();
  }

  public Optional<Instant> latestSuccess() {
    return repository.latestSuccess();
  }

  /** Lê e grava na thread de escrita: atualizações concorrentes não se perdem. */
  private void upsert(String integration, UnaryOperator<IntegrationStatus> change) {
    writer.run(() -> {
      IntegrationStatus current =
          repository.find(integration).orElseGet(() -> IntegrationStatus.untracked(integration));
      repository.save(change.apply(current));
    });
  }
}
