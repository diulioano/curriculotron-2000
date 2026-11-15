package dev.curriculotron.auth.domain;

import java.time.Duration;

/** Recusa de setup/login. O adaptador web traduz para status HTTP e mensagem. */
public class AuthException extends RuntimeException {

  private final AuthError error;
  private final Duration retryAfter;

  public AuthException(AuthError error) {
    this(error, null);
  }

  private AuthException(AuthError error, Duration retryAfter) {
    super(error.name());
    this.error = error;
    this.retryAfter = retryAfter;
  }

  public static AuthException locked(Duration retryAfter) {
    return new AuthException(AuthError.TOO_MANY_ATTEMPTS, retryAfter);
  }

  public AuthError error() {
    return error;
  }

  /** Só para {@link AuthError#TOO_MANY_ATTEMPTS}. */
  public Duration retryAfter() {
    return retryAfter;
  }
}
