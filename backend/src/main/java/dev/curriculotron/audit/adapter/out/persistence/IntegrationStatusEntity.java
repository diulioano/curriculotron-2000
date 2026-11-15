package dev.curriculotron.audit.adapter.out.persistence;

import dev.curriculotron.audit.domain.IntegrationState;
import dev.curriculotron.audit.domain.IntegrationStatus;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "integration_status")
class IntegrationStatusEntity extends AssignedIdEntity<String> {

  @Id private String integration;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "last_success_at")
  private Instant lastSuccessAt;

  @Enumerated(EnumType.STRING)
  private IntegrationState state;

  private String detail;

  @Column(name = "tracking_started_at")
  private Instant trackingStartedAt;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected IntegrationStatusEntity() {}

  static IntegrationStatusEntity create(String integration, Instant now) {
    IntegrationStatusEntity e = new IntegrationStatusEntity();
    e.integration = integration;
    e.createdAt = now;
    return e;
  }

  void apply(IntegrationStatus s, Instant now) {
    lastAttemptAt = s.lastAttemptAt();
    lastSuccessAt = s.lastSuccessAt();
    state = s.state();
    detail = s.detail();
    trackingStartedAt = s.trackingStartedAt();
    updatedAt = now;
  }

  IntegrationStatus toDomain() {
    return new IntegrationStatus(
        integration, lastAttemptAt, lastSuccessAt, state, detail, trackingStartedAt);
  }

  Instant getLastSuccessAt() {
    return lastSuccessAt;
  }

  @Override
  public String getId() {
    return integration;
  }
}
