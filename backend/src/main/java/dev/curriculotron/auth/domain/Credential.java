package dev.curriculotron.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Senha única do app (D-06) e controle de tentativas. Após {@code maxAttempts} erros seguidos, fica
 * bloqueada por {@code lockDuration} e o contador recomeça.
 */
public record Credential(String passwordHash, int failedAttempts, Instant lockedUntil) {

  public static Credential initial(String passwordHash) {
    return new Credential(passwordHash, 0, null);
  }

  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  public Duration lockRemainingAt(Instant now) {
    return isLockedAt(now) ? Duration.between(now, lockedUntil) : Duration.ZERO;
  }

  public boolean hasFailures() {
    return failedAttempts > 0 || lockedUntil != null;
  }

  public Credential withFailuresCleared() {
    return new Credential(passwordHash, 0, null);
  }

  public Credential withFailure(int maxAttempts, Duration lockDuration, Instant now) {
    int attempts = failedAttempts + 1;
    if (attempts >= maxAttempts) {
      return new Credential(passwordHash, 0, now.plus(lockDuration));
    }
    return new Credential(passwordHash, attempts, lockedUntil);
  }
}
