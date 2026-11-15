package dev.curriculotron.auth.application.port.out;

import dev.curriculotron.auth.domain.Credential;
import java.util.Optional;

/** Linha única {@code app_credential}. */
public interface CredentialRepository {

  Optional<Credential> find();

  boolean exists();

  void save(Credential credential);
}
