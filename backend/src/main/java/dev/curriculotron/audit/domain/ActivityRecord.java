package dev.curriculotron.audit.domain;

import java.time.Instant;

/** Linha gravada do ActivityLog (append-only). */
public record ActivityRecord(
    String id,
    String taskRunId,
    String author,
    String action,
    String targetType,
    String targetId,
    String evidenceRef,
    ActivityResult result,
    String error,
    Instant at) {

  public static ActivityRecord of(String id, ActivityEntry e, Instant at) {
    return new ActivityRecord(
        id,
        e.taskRunId(),
        e.author(),
        e.action(),
        e.targetType(),
        e.targetId(),
        e.evidenceRef(),
        e.result(),
        e.error(),
        at);
  }
}
