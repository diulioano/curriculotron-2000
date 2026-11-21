package dev.curriculotron.opportunities.domain;

import java.time.Instant;
import java.util.Objects;

/** Evento da linha do tempo de uma vaga, projetado de fontes, seleção e registro de atividades. */
public record OpportunityTimelineEvent(
    String id,
    TimelineEventKind kind,
    String actorLabel,
    String description,
    String note,
    Instant at) {
  public enum TimelineEventKind {
    discovered,
    selected_today,
    external_opened,
    corrected,
    discarded,
    restored,
    applied
  }

  public OpportunityTimelineEvent {
    Objects.requireNonNull(id);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(actorLabel);
    Objects.requireNonNull(description);
    Objects.requireNonNull(at);
  }
}
