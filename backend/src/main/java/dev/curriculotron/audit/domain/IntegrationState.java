package dev.curriculotron.audit.domain;

/** Estado exibido de uma integração (FR-017). */
public enum IntegrationState {
  ok,
  degraded,
  disconnected,
  paused
}
