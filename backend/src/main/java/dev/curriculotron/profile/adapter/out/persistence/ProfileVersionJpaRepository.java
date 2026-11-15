package dev.curriculotron.profile.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProfileVersionJpaRepository extends JpaRepository<ProfileVersionEntity, String> {

  Optional<ProfileVersionEntity> findFirstBySourceHash(String sourceHash);
}
