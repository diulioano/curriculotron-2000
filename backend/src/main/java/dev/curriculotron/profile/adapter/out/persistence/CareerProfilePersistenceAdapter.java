package dev.curriculotron.profile.adapter.out.persistence;

import dev.curriculotron.profile.application.port.out.CareerProfileRepository;
import dev.curriculotron.profile.domain.CareerProfile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class CareerProfilePersistenceAdapter implements CareerProfileRepository {
  private final CareerProfileJpaRepository profiles;

  CareerProfilePersistenceAdapter(CareerProfileJpaRepository profiles) {
    this.profiles = profiles;
  }

  @Override
  public Optional<Stored> current() {
    return profiles
        .findById(CareerProfileEntity.SINGLETON_ID)
        .map(entity -> new Stored(entity.profile(), entity.updatedAt()));
  }

  @Override
  public void save(CareerProfile profile, Instant at) {
    var entity =
        profiles
            .findById(CareerProfileEntity.SINGLETON_ID)
            .orElseGet(() -> CareerProfileEntity.create(at));
    entity.apply(profile, at);
    profiles.saveAndFlush(entity);
  }
}
