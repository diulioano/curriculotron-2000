package dev.curriculotron.auth.domain;

/** Motivos de recusa no setup e no login. */
public enum AuthError {
  SETUP_REQUIRED,
  ALREADY_CONFIGURED,
  WEAK_PASSWORD,
  INVALID_PASSWORD,
  TOO_MANY_ATTEMPTS
}
