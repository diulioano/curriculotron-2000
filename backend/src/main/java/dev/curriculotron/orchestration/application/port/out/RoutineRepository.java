package dev.curriculotron.orchestration.application.port.out;

import dev.curriculotron.orchestration.domain.MissedRoutine;
import dev.curriculotron.orchestration.domain.Routine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoutineRepository {

  void save(Routine routine);

  Optional<Routine> find(String id);

  List<Routine> findAll();

  /** Rotinas ligadas com {@code nextRunAt <= now}. */
  List<Routine> due(Instant now);

  /** Idempotente: a mesma ocorrência não é registrada duas vezes. */
  void recordMissed(String routineId, Instant scheduledFor);

  List<MissedRoutine> unacknowledgedMissed();
}
