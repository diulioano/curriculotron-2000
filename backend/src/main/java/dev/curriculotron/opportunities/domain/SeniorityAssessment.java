package dev.curriculotron.opportunities.domain;

import java.util.Objects;

/** Nível da vaga com a origem da informação: declarado, inferido ou ausente. */
public record SeniorityAssessment(String label, Status status, String reason) {
  public enum Status {
    fact,
    inferred,
    absent,
    /** Nível derivado do título que a pessoa corrigiu, não da evidência original. */
    corrected_title
  }

  public SeniorityAssessment {
    Objects.requireNonNull(status);
  }
}
