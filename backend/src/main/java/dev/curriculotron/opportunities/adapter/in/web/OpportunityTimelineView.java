package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.domain.OpportunityTimelineEvent;

/**
 * Evento da linha do tempo no detalhe da vaga; {@code kind} usa o nome de transporte em snake_case.
 */
public record OpportunityTimelineView(
    String id, String kind, String actorLabel, String description, String note, String at) {
  static OpportunityTimelineView of(OpportunityTimelineEvent event) {
    return new OpportunityTimelineView(
        event.id(),
        event.kind().name(),
        event.actorLabel(),
        event.description(),
        event.note(),
        event.at().toString());
  }
}
