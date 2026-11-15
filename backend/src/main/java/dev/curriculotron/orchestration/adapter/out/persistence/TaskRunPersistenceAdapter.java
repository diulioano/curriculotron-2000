package dev.curriculotron.orchestration.adapter.out.persistence;

import dev.curriculotron.orchestration.application.port.out.TaskRunRepository;
import dev.curriculotron.orchestration.domain.TaskRun;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.shared.AppClock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
class TaskRunPersistenceAdapter implements TaskRunRepository {

  private final TaskRunJpaRepository jpa;
  private final AppClock clock;

  TaskRunPersistenceAdapter(TaskRunJpaRepository jpa, AppClock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public void save(TaskRun run) {
    TaskRunEntity entity = jpa.findById(run.id()).orElseGet(() -> TaskRunEntity.create(run.id()));
    entity.apply(run, clock.now());
    jpa.save(entity);
  }

  @Override
  public Optional<TaskRun> find(String id) {
    return jpa.findById(id).map(TaskRunEntity::toDomain);
  }

  @Override
  public Optional<TaskRun> findByIdempotencyKey(String key) {
    return jpa.findByIdempotencyKey(key).map(TaskRunEntity::toDomain);
  }

  @Override
  public List<TaskRun> recent(int limit) {
    return jpa.findAllByOrderByCreatedAtDescIdDesc(Limit.of(limit)).stream()
        .map(TaskRunEntity::toDomain)
        .toList();
  }

  @Override
  public List<TaskRun> inState(TaskRunState state) {
    return jpa.findByStateOrderByCreatedAtAsc(state).stream().map(TaskRunEntity::toDomain).toList();
  }
}
