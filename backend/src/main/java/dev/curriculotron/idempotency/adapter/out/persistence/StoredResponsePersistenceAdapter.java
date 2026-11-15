package dev.curriculotron.idempotency.adapter.out.persistence;

import dev.curriculotron.idempotency.application.port.out.StoredResponseRepository;
import dev.curriculotron.idempotency.domain.StoredResponse;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class StoredResponsePersistenceAdapter implements StoredResponseRepository {

  private final IdempotencyKeyJpaRepository jpa;

  StoredResponsePersistenceAdapter(IdempotencyKeyJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<StoredResponse> find(String key) {
    return jpa.findById(key).map(IdempotencyKeyEntity::toDomain);
  }

  @Override
  public boolean exists(String key) {
    return jpa.existsById(key);
  }

  @Override
  public void save(StoredResponse response) {
    jpa.save(IdempotencyKeyEntity.from(response));
  }

  @Override
  public void deleteCreatedBefore(Instant cutoff) {
    jpa.deleteCreatedBefore(cutoff);
  }
}
