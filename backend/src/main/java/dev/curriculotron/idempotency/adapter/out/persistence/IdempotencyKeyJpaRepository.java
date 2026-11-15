package dev.curriculotron.idempotency.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, String> {

  @Modifying
  @Query("delete from IdempotencyKeyEntity e where e.createdAt < :cutoff")
  int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
