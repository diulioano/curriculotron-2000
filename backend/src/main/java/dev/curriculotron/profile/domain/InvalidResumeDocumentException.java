package dev.curriculotron.profile.domain;

/** Documento de currículo inválido, ilegível ou fora dos limites aceitos na importação (US006). */
public final class InvalidResumeDocumentException extends RuntimeException {
  public InvalidResumeDocumentException(String message) {
    super(message);
  }

  public InvalidResumeDocumentException(String message, Throwable cause) {
    super(message, cause);
  }
}
