package dev.curriculotron.profile.application.port.out;

import dev.curriculotron.profile.domain.ProfileVersion;
import java.util.Optional;

public interface ProfileVersionRepository {

  Optional<String> findIdBySourceHash(String sourceHash);

  /** Grava a versão e seus fatos. */
  void save(ProfileVersion version);
}
