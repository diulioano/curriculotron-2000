package dev.curriculotron.auth.adapter.out.persistence;

import static dev.curriculotron.auth.adapter.out.persistence.AppCredentialEntity.SINGLETON_ID;

import dev.curriculotron.auth.application.port.out.CredentialRepository;
import dev.curriculotron.auth.domain.Credential;
import dev.curriculotron.shared.AppClock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class CredentialPersistenceAdapter implements CredentialRepository {

  private final AppCredentialJpaRepository jpa;
  private final AppClock clock;

  CredentialPersistenceAdapter(AppCredentialJpaRepository jpa, AppClock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public Optional<Credential> find() {
    return jpa.findById(SINGLETON_ID).map(AppCredentialEntity::toDomain);
  }

  @Override
  public boolean exists() {
    return jpa.existsById(SINGLETON_ID);
  }

  @Override
  public void save(Credential credential) {
    Instant now = clock.now();
    AppCredentialEntity entity =
        jpa.findById(SINGLETON_ID).orElseGet(() -> AppCredentialEntity.create(now));
    entity.apply(credential, now);
    jpa.save(entity);
  }
}
