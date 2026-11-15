package dev.curriculotron.opportunities.domain;

import java.time.Instant;
import java.util.Objects;

public record VacancyEvidence(
    String id,
    String opportunityId,
    String sourceId,
    String sourceKind,
    String title,
    String location,
    String text,
    Completeness completeness,
    String completenessReason,
    long contentRevision,
    Instant fetchedAt,
    String sourceUrl) {
  public enum Completeness {
    complete,
    partial,
    unknown
  }

  public VacancyEvidence {
    if (id == null
        || id.isBlank()
        || opportunityId == null
        || opportunityId.isBlank()
        || sourceKind == null
        || sourceKind.isBlank())
      throw new IllegalArgumentException("Evidence identity is required");
    Objects.requireNonNull(completeness);
    if (contentRevision <= 0)
      throw new IllegalArgumentException("Evidence revision must be positive");
    if (text != null && text.length() > 100_000)
      throw new IllegalArgumentException("Evidence text is too long");
  }
}
