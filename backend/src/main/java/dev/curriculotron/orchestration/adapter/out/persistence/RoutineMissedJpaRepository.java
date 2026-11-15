package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.orchestration.domain.MissedRoutine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RoutineMissedJpaRepository extends JpaRepository<RoutineMissedEntity, RoutineMissedId> {

  @Query(
      """
      select new dev.curriculotron.orchestration.domain.MissedRoutine(
          m.id.routineId, r.purpose, m.id.scheduledFor)
      from RoutineMissedEntity m join m.routine r
      where m.acknowledged = false
      order by m.id.scheduledFor
      """)
  List<MissedRoutine> findUnacknowledged();
}
