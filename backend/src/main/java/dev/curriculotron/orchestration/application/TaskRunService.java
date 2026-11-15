package dev.curriculotron.orchestration.application;

import dev.curriculotron.orchestration.application.port.out.TaskRunRepository;
import dev.curriculotron.orchestration.domain.CreatedTaskRun;
import dev.curriculotron.orchestration.domain.NewTaskRun;
import dev.curriculotron.orchestration.domain.PauseReason;
import dev.curriculotron.orchestration.domain.TaskRun;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

/** Casos de uso do ciclo de vida de {@code TaskRun}. As regras ficam em {@link TaskRun}. */
@Service
public class TaskRunService {

  private final TaskRunRepository runs;
  private final WriteExecutor writer;
  private final AppClock clock;

  public TaskRunService(TaskRunRepository runs, WriteExecutor writer, AppClock clock) {
    this.runs = runs;
    this.writer = writer;
    this.clock = clock;
  }

  public CreatedTaskRun createOrGet(NewTaskRun req) {
    return writer.write(() -> {
      Optional<TaskRun> existing = runs.findByIdempotencyKey(req.idempotencyKey());
      if (existing.isPresent()) {
        return new CreatedTaskRun(existing.get(), false);
      }
      TaskRun run = TaskRun.queued(Ids.newId(), req, clock.now());
      runs.save(run);
      return new CreatedTaskRun(run, true);
    });
  }

  public TaskRun start(String id) {
    return transition(id, TaskRunState.running, null, null);
  }

  public TaskRun complete(String id) {
    return transition(id, TaskRunState.completed, null, null);
  }

  public TaskRun fail(String id, String errorSummary) {
    return transition(id, TaskRunState.failed, null, errorSummary);
  }

  public TaskRun pause(String id, PauseReason reason, String detail) {
    if (reason == null) {
      throw new IllegalArgumentException("Pausa exige motivo");
    }
    return transition(id, TaskRunState.paused, reason, detail);
  }

  /** Retomada após pausa: volta a {@code running} mantendo o checkpoint. */
  public TaskRun resume(String id) {
    return transition(id, TaskRunState.running, null, null);
  }

  public TaskRun saveCheckpoint(String id, Map<String, Object> patch) {
    return update(id, run -> run.withCheckpoint(patch));
  }

  public void recordSources(String id, List<String> consulted, List<String> failed) {
    update(id, run -> run.withSources(consulted, failed));
  }

  public Optional<TaskRun> find(String id) {
    return runs.find(id);
  }

  public Optional<TaskRun> findByIdempotencyKey(String key) {
    return runs.findByIdempotencyKey(key);
  }

  public List<TaskRun> recent(int limit) {
    return runs.recent(limit);
  }

  public List<TaskRun> inState(TaskRunState state) {
    return runs.inState(state);
  }

  private TaskRun transition(String id, TaskRunState to, PauseReason reason, String error) {
    return update(id, run -> run.transitionTo(to, reason, error, clock.now()));
  }

  private TaskRun update(String id, UnaryOperator<TaskRun> change) {
    return writer.write(() -> {
      TaskRun run = runs.find(id).orElseThrow(() -> new IllegalArgumentException("TaskRun " + id));
      TaskRun changed = change.apply(run);
      runs.save(changed);
      return changed;
    });
  }
}
