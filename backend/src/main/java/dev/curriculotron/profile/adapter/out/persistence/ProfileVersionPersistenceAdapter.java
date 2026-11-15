package dev.curriculotron.profile.adapter.out.persistence;

import dev.curriculotron.profile.application.port.out.ProfileVersionRepository;
import dev.curriculotron.profile.domain.ProfileVersion;
import dev.curriculotron.shared.AppClock;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ProfileVersionPersistenceAdapter implements ProfileVersionRepository {

  private final ProfileVersionJpaRepository jpa;
  private final AppClock clock;

  ProfileVersionPersistenceAdapter(ProfileVersionJpaRepository jpa, AppClock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public Optional<String> findIdBySourceHash(String sourceHash) {
    return jpa.findFirstBySourceHash(sourceHash).map(ProfileVersionEntity::getId);
  }

  @Override
  public void save(ProfileVersion version) {
    jpa.save(ProfileVersionEntity.from(version, clock.now()));
  }
}
