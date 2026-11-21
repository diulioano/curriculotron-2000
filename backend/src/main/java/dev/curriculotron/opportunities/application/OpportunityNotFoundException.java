package dev.curriculotron.opportunities.application;

/** Vaga inexistente; traduzida para HTTP 404 pelos adaptadores de entrada. */
public final class OpportunityNotFoundException extends RuntimeException {
  public OpportunityNotFoundException(String id) {
    super("Vaga não encontrada: " + id);
  }
}
