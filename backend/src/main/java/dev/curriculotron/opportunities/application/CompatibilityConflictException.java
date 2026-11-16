package dev.curriculotron.opportunities.application;

import java.util.Objects;

public final class CompatibilityConflictException extends RuntimeException {
  public enum Reason {
    CRITERIA_VERSION_CONFLICT(
        "criteria_version_conflict",
        "Os critérios foram alterados. Recarregue antes de reavaliar."),
    REASSESSMENT_RUNNING(
        "reassessment_running", "Já existe uma reavaliação em andamento para esta instalação."),
    EVIDENCE_CHANGED(
        "evidence_changed",
        "As vagas mudaram durante a reavaliação. Consulte o status e tente novamente.");

    private final String code;
    private final String message;

    Reason(String code, String message) {
      this.code = code;
      this.message = message;
    }

    public String code() {
      return code;
    }

    public String message() {
      return message;
    }
  }

  private final Reason reason;

  public CompatibilityConflictException(Reason reason) {
    super(Objects.requireNonNull(reason).message());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public String code() {
    return reason.code();
  }
}
