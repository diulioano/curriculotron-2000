package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.orchestration.domain.Routine;
import dev.curriculotron.orchestration.domain.RoutineKind;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "routine")
class RoutineEntity extends AssignedIdEntity<String> {

  @Id private String id;

  private String purpose;

  @Column(name = "owner_bot_id")
  private String ownerBotId;

  @Enumerated(EnumType.STRING)
  private RoutineKind kind;

  @Column(name = "time_local")
  private LocalTime timeLocal;

  private boolean enabled;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @Column(name = "last_run_at")
  private Instant lastRunAt;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected RoutineEntity() {}

  static RoutineEntity create(String id, Instant now) {
    RoutineEntity e = new RoutineEntity();
    e.id = id;
    e.createdAt = now;
    return e;
  }

  void apply(Routine r, Instant now) {
    purpose = r.purpose();
    ownerBotId = r.ownerBotId();
    kind = r.kind();
    timeLocal = r.timeLocal();
    enabled = r.enabled();
    nextRunAt = r.nextRunAt();
    lastRunAt = r.lastRunAt();
    updatedAt = now;
  }

  Routine toDomain() {
    return new Routine(id, purpose, ownerBotId, kind, timeLocal, enabled, nextRunAt, lastRunAt);
  }

  @Override
  public String getId() {
    return id;
  }
}
