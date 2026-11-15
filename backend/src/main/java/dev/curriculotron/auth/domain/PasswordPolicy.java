package dev.curriculotron.auth.domain;

/** Regras da senha do app (D-06). */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 10;
  public static final int MAX_LENGTH = 256;

  private PasswordPolicy() {}

  public static boolean isAcceptable(String password) {
    return password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
  }

  /** Senhas acima do limite nem chegam ao hash (evita custo de Argon2 com entradas enormes). */
  public static boolean isWithinMaxLength(String password) {
    return password != null && password.length() <= MAX_LENGTH;
  }
}
