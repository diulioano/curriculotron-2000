package dev.curriculotron.idempotency.application.port.out;

import dev.curriculotron.idempotency.domain.StoredResponse;
import java.time.Instant;
import java.util.Optional;

public interface StoredResponseRepository {

  Optional<StoredResponse> find(String key);

  boolean exists(String key);

  void save(StoredResponse response);

  void deleteCreatedBefore(Instant cutoff);
}
