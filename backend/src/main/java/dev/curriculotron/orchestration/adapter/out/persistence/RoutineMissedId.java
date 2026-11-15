package dev.curriculotron.orchestration.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Chave composta de {@code routine_missed}: uma linha por ocorrência perdida. */
@Embeddable
class RoutineMissedId implements Serializable {

  @Column(name = "routine_id")
  private String routineId;

  @Column(name = "scheduled_for")
  private Instant scheduledFor;

  protected RoutineMissedId() {}

  RoutineMissedId(String routineId, Instant scheduledFor) {
    this.routineId = routineId;
    this.scheduledFor = scheduledFor;
  }

  String routineId() {
    return routineId;
  }

  Instant scheduledFor() {
    return scheduledFor;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RoutineMissedId other
        && Objects.equals(routineId, other.routineId)
        && Objects.equals(scheduledFor, other.scheduledFor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(routineId, scheduledFor);
  }
}
