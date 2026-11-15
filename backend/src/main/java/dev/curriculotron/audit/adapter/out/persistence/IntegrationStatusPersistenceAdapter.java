package dev.curriculotron.audit.adapter.out.persistence;

import dev.curriculotron.audit.application.port.out.IntegrationStatusRepository;
import dev.curriculotron.audit.domain.IntegrationStatus;
import dev.curriculotron.shared.AppClock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class IntegrationStatusPersistenceAdapter implements IntegrationStatusRepository {

  private final IntegrationStatusJpaRepository jpa;
  private final AppClock clock;

  IntegrationStatusPersistenceAdapter(IntegrationStatusJpaRepository jpa, AppClock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public Optional<IntegrationStatus> find(String integration) {
    return jpa.findById(integration).map(IntegrationStatusEntity::toDomain);
  }

  @Override
  public List<IntegrationStatus> findAll() {
    return jpa.findAllByOrderByIntegrationAsc().stream()
        .map(IntegrationStatusEntity::toDomain)
        .toList();
  }

  @Override
  public void save(IntegrationStatus status) {
    Instant now = clock.now();
    IntegrationStatusEntity entity =
        jpa.findById(status.integration())
            .orElseGet(() -> IntegrationStatusEntity.create(status.integration(), now));
    entity.apply(status, now);
    jpa.save(entity);
  }

  @Override
  public Optional<Instant> latestSuccess() {
    return jpa.findFirstByLastSuccessAtNotNullOrderByLastSuccessAtDesc()
        .map(IntegrationStatusEntity::getLastSuccessAt);
  }
}
