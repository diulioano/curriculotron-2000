package dev.curriculotron.opportunities.application;

import dev.curriculotron.opportunities.domain.CompatibilityRefresh.State;
import java.time.Instant;

public record CompatibilityStatus(
    long criteriaVersion,
    State state,
    boolean stale,
    long assessedCount,
    long remainingCount,
    Instant startedAt,
    Instant finishedAt,
    String errorCode) {}
