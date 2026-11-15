package dev.curriculotron.orchestration.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoutineJpaRepository extends JpaRepository<RoutineEntity, String> {

  List<RoutineEntity> findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant now);

  List<RoutineEntity> findAllByOrderByPurposeAsc();
}
