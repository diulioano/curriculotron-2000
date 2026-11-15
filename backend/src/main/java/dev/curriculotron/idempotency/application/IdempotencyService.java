package dev.curriculotron.idempotency.application;

import dev.curriculotron.idempotency.application.port.out.StoredResponseRepository;
import dev.curriculotron.idempotency.domain.StoredResponse;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Memoriza respostas 2xx por chave durante {@link #RETENTION}. */
@Service
public class IdempotencyService {

  public static final Duration RETENTION = Duration.ofDays(7);

  private final StoredResponseRepository repository;
  private final WriteExecutor writer;
  private final AppClock clock;

  public IdempotencyService(
      StoredResponseRepository repository, WriteExecutor writer, AppClock clock) {
    this.repository = repository;
    this.writer = writer;
    this.clock = clock;
  }

  public Optional<StoredResponse> find(String key) {
    return repository.find(key);
  }

  /** Grava a resposta e descarta as expiradas. Se a chave já existe, mantém a primeira. */
  public void remember(
      String key, String method, String path, int status, String contentType, byte[] body) {
    Instant now = clock.now();
    writer.run(() -> {
      repository.deleteCreatedBefore(now.minus(RETENTION));
      if (!repository.exists(key)) {
        repository.save(new StoredResponse(key, method, path, status, contentType, body, now));
      }
    });
  }
}
