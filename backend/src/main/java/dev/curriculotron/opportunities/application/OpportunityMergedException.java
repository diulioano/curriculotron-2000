package dev.curriculotron.opportunities.application;

/** A vaga foi fundida em outra; correções devem ser feitas na vaga sobrevivente. */
public final class OpportunityMergedException extends RuntimeException {
  private final String survivorId;

  public OpportunityMergedException(String opportunityId, String survivorId) {
    super("Vaga " + opportunityId + " foi fundida em " + survivorId);
    this.survivorId = survivorId;
  }

  public String survivorId() {
    return survivorId;
  }
}
