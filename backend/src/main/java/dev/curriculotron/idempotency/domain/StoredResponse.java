package dev.curriculotron.idempotency.domain;

import java.time.Instant;

/** Resposta 2xx memorizada para uma {@code Idempotency-Key} (contracts/panel-api.md, NFR-004). */
public record StoredResponse(
    String key,
    String method,
    String path,
    int status,
    String contentType,
    byte[] body,
    Instant createdAt) {

  /** A mesma chave só vale para a mesma operação. */
  public boolean matches(String method, String path) {
    return this.method.equals(method) && this.path.equals(path);
  }
}
