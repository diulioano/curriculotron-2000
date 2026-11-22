package dev.curriculotron.profile.domain;

/** Perfil que viola uma invariante; {@link #field()} indica o campo, sem dados da pessoa. */
public final class InvalidProfileException extends RuntimeException {
  private final String field;

  public InvalidProfileException(String field) {
    super("Invalid profile field: " + field);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
