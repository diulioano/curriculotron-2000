package dev.curriculotron.profile.application;

/** A versão enviada não é a atual: outra edição foi salva antes. */
public final class ProfileVersionConflictException extends RuntimeException {
  public ProfileVersionConflictException() {
    super("Career profile version conflict");
  }
}
