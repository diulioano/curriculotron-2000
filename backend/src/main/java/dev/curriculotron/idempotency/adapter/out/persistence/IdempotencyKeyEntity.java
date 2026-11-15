package dev.curriculotron.idempotency.adapter.out.persistence;

import dev.curriculotron.idempotency.domain.StoredResponse;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "idempotency_key")
class IdempotencyKeyEntity extends AssignedIdEntity<String> {

  @Id
  @Column(name = "idem_key")
  private String key;

  private String method;
  private String path;
  private int status;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "response_body")
  private byte[] responseBody;

  @Column(name = "created_at")
  private Instant createdAt;

  protected IdempotencyKeyEntity() {}

  static IdempotencyKeyEntity from(StoredResponse r) {
    IdempotencyKeyEntity e = new IdempotencyKeyEntity();
    e.key = r.key();
    e.method = r.method();
    e.path = r.path();
    e.status = r.status();
    e.contentType = r.contentType();
    e.responseBody = r.body();
    e.createdAt = r.createdAt();
    return e;
  }

  StoredResponse toDomain() {
    return new StoredResponse(key, method, path, status, contentType, responseBody, createdAt);
  }

  @Override
  public String getId() {
    return key;
  }
}
