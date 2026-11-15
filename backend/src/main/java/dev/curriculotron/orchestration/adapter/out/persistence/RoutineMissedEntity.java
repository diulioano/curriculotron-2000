package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "routine_missed")
class RoutineMissedEntity extends AssignedIdEntity<RoutineMissedId> {

  @EmbeddedId private RoutineMissedId id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "routine_id", insertable = false, updatable = false)
  private RoutineEntity routine;

  @Column(name = "recorded_at")
  private Instant recordedAt;

  private boolean acknowledged;

  protected RoutineMissedEntity() {}

  RoutineMissedEntity(RoutineMissedId id, Instant recordedAt) {
    this.id = id;
    this.recordedAt = recordedAt;
  }

  @Override
  public RoutineMissedId getId() {
    return id;
  }
}
