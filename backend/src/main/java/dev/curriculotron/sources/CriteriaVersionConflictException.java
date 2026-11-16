package dev.curriculotron.sources;

public final class CriteriaVersionConflictException extends RuntimeException {
  public CriteriaVersionConflictException() {
    super("Criteria version conflict");
  }
}
