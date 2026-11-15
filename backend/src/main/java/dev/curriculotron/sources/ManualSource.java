package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import java.util.List;

/** One explicit user submission. Does not navigate or execute supplied content. */
public record ManualSource(String url, String text) implements JobSource {
  @Override
  public SourceResult fetch() {
    boolean hasUrl = url != null && !url.isBlank();
    boolean hasText = text != null && !text.isBlank();
    if (!hasUrl && !hasText) {
      throw new IllegalArgumentException("Informe uma URL ou o texto da vaga.");
    }
    String canonical = hasUrl ? CanonicalUrl.normalize(url) : null;
    return new SourceResult(
        List.of(
            new Item(
                "manual",
                null,
                canonical,
                hasText ? text : null,
                null,
                null,
                null,
                Completeness.unknown,
                "user_supplied_unverified")),
        List.of(),
        List.of(
            "Conteúdo informado pelo usuário; disponibilidade e detalhes da vaga não verificados."));
  }
}
