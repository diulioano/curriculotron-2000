package dev.curriculotron.audit.domain;

/** Resultado de uma ação registrada no ActivityLog. */
public enum ActivityResult {
  ok,
  failed,
  refused,
  skipped
}
