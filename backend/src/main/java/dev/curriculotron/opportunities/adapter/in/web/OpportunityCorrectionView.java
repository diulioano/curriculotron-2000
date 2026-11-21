package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.domain.OpportunityCorrectionEntry;

/** Correção exibida no detalhe da vaga; {@code fieldName} usa o nome persistido em snake_case. */
public record OpportunityCorrectionView(
    String id,
    String fieldName,
    String previousValue,
    String correctedValue,
    String reason,
    String authorLabel,
    String correctedAt) {
  static final String USER_LABEL = "você";

  static OpportunityCorrectionView of(OpportunityCorrectionEntry entry) {
    return new OpportunityCorrectionView(
        entry.id(),
        entry.fieldName().wireValue(),
        entry.previousValue(),
        entry.correctedValue(),
        entry.reason(),
        USER_LABEL,
        entry.correctedAt().toString());
  }
}
