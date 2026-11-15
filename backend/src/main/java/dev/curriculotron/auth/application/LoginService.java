package dev.curriculotron.auth.application;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.audit.domain.ActivityResult;
import dev.curriculotron.auth.application.port.out.CredentialRepository;
import dev.curriculotron.auth.application.port.out.PasswordHasher;
import dev.curriculotron.auth.domain.AuthError;
import dev.curriculotron.auth.domain.AuthException;
import dev.curriculotron.auth.domain.Credential;
import dev.curriculotron.auth.domain.PasswordPolicy;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.config.CurriculotronProperties;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Senha única do app (D-06): definida no primeiro uso, hash Argon2id, bloqueio temporário após
 * tentativas erradas repetidas.
 */
@Service
public class LoginService {

  private final CredentialRepository credentials;
  private final PasswordHasher hasher;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;
  private final int maxAttempts;
  private final Duration lockDuration;

  public LoginService(
      CredentialRepository credentials,
      PasswordHasher hasher,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity,
      CurriculotronProperties props) {
    this.credentials = credentials;
    this.hasher = hasher;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
    this.maxAttempts = props.login().maxAttempts();
    this.lockDuration = props.login().lockDuration();
  }

  public boolean isSetupDone() {
    return credentials.exists();
  }

  public void setup(String password) {
    if (!PasswordPolicy.isAcceptable(password)) {
      throw new AuthException(AuthError.WEAK_PASSWORD);
    }
    String hash = hasher.hash(password);
    boolean created =
        writer.write(() -> {
          if (credentials.exists()) {
            return false;
          }
          credentials.save(Credential.initial(hash));
          activity.record(ActivityEntry.system("app.setup", ActivityResult.ok));
          return true;
        });
    if (!created) {
      throw new AuthException(AuthError.ALREADY_CONFIGURED);
    }
  }

  /** Verifica a senha; lança {@link AuthException} quando recusa. */
  public void authenticate(String password) {
    Credential cred =
        credentials.find().orElseThrow(() -> new AuthException(AuthError.SETUP_REQUIRED));

    Instant now = clock.now();
    if (cred.isLockedAt(now)) {
      throw AuthException.locked(cred.lockRemainingAt(now));
    }

    boolean ok =
        PasswordPolicy.isWithinMaxLength(password) && hasher.matches(password, cred.passwordHash());
    if (ok) {
      if (cred.hasFailures()) {
        writer.run(() -> credentials.save(cred.withFailuresCleared()));
      }
      return;
    }

    boolean locked =
        writer.write(() -> {
          // Relê dentro da thread de escrita para contar tentativas concorrentes corretamente.
          Credential updated =
              credentials.find().orElseThrow().withFailure(maxAttempts, lockDuration, clock.now());
          credentials.save(updated);
          boolean nowLocked = updated.isLockedAt(clock.now());
          activity.record(
              ActivityEntry.system(
                  nowLocked ? "session.login_locked" : "session.login_failed",
                  ActivityResult.refused));
          return nowLocked;
        });
    if (locked) {
      throw AuthException.locked(lockDuration);
    }
    throw new AuthException(AuthError.INVALID_PASSWORD);
  }
}
