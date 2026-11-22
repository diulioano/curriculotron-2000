package dev.curriculotron.profile.application.port.out;

import dev.curriculotron.profile.domain.CareerProfile;
import java.time.Instant;
import java.util.Optional;

public interface CareerProfileRepository {
  record Stored(CareerProfile profile, Instant updatedAt) {}

  Optional<Stored> current();

  void save(CareerProfile profile, Instant at);
}
