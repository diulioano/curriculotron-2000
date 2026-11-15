package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.orchestration.domain.MissedRoutine;
import dev.curriculotron.orchestration.domain.Routine;
import dev.curriculotron.shared.AppClock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class RoutinePersistenceAdapter implements RoutineRepository {

  private final RoutineJpaRepository routines;
  private final RoutineMissedJpaRepository missed;
  private final AppClock clock;

  RoutinePersistenceAdapter(
      RoutineJpaRepository routines, RoutineMissedJpaRepository missed, AppClock clock) {
    this.routines = routines;
    this.missed = missed;
    this.clock = clock;
  }

  @Override
  public void save(Routine routine) {
    Instant now = clock.now();
    RoutineEntity entity =
        routines.findById(routine.id()).orElseGet(() -> RoutineEntity.create(routine.id(), now));
    entity.apply(routine, now);
    routines.save(entity);
  }

  @Override
  public Optional<Routine> find(String id) {
    return routines.findById(id).map(RoutineEntity::toDomain);
  }

  @Override
  public List<Routine> findAll() {
    return routines.findAllByOrderByPurposeAsc().stream().map(RoutineEntity::toDomain).toList();
  }

  @Override
  public List<Routine> due(Instant now) {
    return routines.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now).stream()
        .map(RoutineEntity::toDomain)
        .toList();
  }

  @Override
  public void recordMissed(String routineId, Instant scheduledFor) {
    var id = new RoutineMissedId(routineId, scheduledFor);
    if (!missed.existsById(id)) {
      missed.save(new RoutineMissedEntity(id, clock.now()));
    }
  }

  @Override
  public List<MissedRoutine> unacknowledgedMissed() {
    return missed.findUnacknowledged();
  }
}
