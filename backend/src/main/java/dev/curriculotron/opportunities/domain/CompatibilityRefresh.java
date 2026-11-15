package dev.curriculotron.opportunities.domain;

import java.time.Instant;
import java.util.Objects;

public record CompatibilityRefresh(
    long criteriaVersion,
    int evaluatorVersion,
    String runId,
    State state,
    Instant startedAt,
    Instant finishedAt,
    String errorCode,
    long assessedCount,
    long remainingCount) {
  public enum State {
    pending,
    running,
    ready,
    failed
  }

  public CompatibilityRefresh {
    Objects.requireNonNull(state);
    if (criteriaVersion <= 0 || evaluatorVersion <= 0 || assessedCount < 0 || remainingCount < 0)
      throw new IllegalArgumentException("Invalid refresh versions or counts");
    if (state == State.running && (runId == null || runId.isBlank() || startedAt == null))
      throw new IllegalArgumentException("Running refresh requires owner and start time");
    if (state == State.ready && remainingCount != 0)
      throw new IllegalArgumentException("Ready refresh cannot have pending assessments");
  }

  public boolean stale() {
    return state != State.ready || remainingCount != 0;
  }
}
