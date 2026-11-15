package dev.curriculotron.auth.adapter.out.persistence;

import dev.curriculotron.auth.domain.Credential;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code app_credential}: linha única ({@code id = 1}). */
@Entity
@Table(name = "app_credential")
class AppCredentialEntity extends AssignedIdEntity<Integer> {

  static final int SINGLETON_ID = 1;

  @Id private Integer id;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "failed_attempts")
  private int failedAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected AppCredentialEntity() {}

  static AppCredentialEntity create(Instant now) {
    AppCredentialEntity e = new AppCredentialEntity();
    e.id = SINGLETON_ID;
    e.createdAt = now;
    return e;
  }

  void apply(Credential c, Instant now) {
    passwordHash = c.passwordHash();
    failedAttempts = c.failedAttempts();
    lockedUntil = c.lockedUntil();
    updatedAt = now;
  }

  Credential toDomain() {
    return new Credential(passwordHash, failedAttempts, lockedUntil);
  }

  @Override
  public Integer getId() {
    return id;
  }
}
