package dev.curriculotron.shared.web;

import java.util.Objects;

/**
 * Pendência individual da página inicial. {@code targetRoute} é sempre uma rota interna do painel
 * (começa com {@code /}, nunca {@code //} nem contém {@code \}).
 */
public record PendingReviewItem(
    String id,
    PendingReviewKind kind,
    String kindLabel,
    String title,
    String summary,
    String targetRoute) {
  public PendingReviewItem {
    Objects.requireNonNull(id);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(kindLabel);
    Objects.requireNonNull(title);
    Objects.requireNonNull(summary);
    if (targetRoute == null
        || !targetRoute.startsWith("/")
        || targetRoute.startsWith("//")
        || targetRoute.contains("\\")) {
      throw new IllegalArgumentException("targetRoute deve ser uma rota interna");
    }
  }
}
