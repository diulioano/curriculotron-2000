package dev.curriculotron.opportunities.domain;

import java.time.Instant;
import java.util.Objects;

/** Uma alteração de campo registrada de forma append-only; valores serializados em texto. */
public record OpportunityCorrectionEntry(
    String id,
    String opportunityId,
    CorrectableOpportunityField fieldName,
    String previousValue,
    String correctedValue,
    String reason,
    String author,
    Instant correctedAt) {
  public OpportunityCorrectionEntry {
    Objects.requireNonNull(id);
    Objects.requireNonNull(opportunityId);
    Objects.requireNonNull(fieldName);
    Objects.requireNonNull(author);
    Objects.requireNonNull(correctedAt);
  }
}
