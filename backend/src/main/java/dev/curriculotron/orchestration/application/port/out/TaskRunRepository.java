package dev.curriculotron.orchestration.application.port.out;

import dev.curriculotron.orchestration.domain.TaskRun;
import dev.curriculotron.orchestration.domain.TaskRunState;
import java.util.List;
import java.util.Optional;

public interface TaskRunRepository {

  void save(TaskRun run);

  Optional<TaskRun> find(String id);

  Optional<TaskRun> findByIdempotencyKey(String key);

  /** Mais recentes primeiro. */
  List<TaskRun> recent(int limit);

  /** Mais antigas primeiro. */
  List<TaskRun> inState(TaskRunState state);
}
