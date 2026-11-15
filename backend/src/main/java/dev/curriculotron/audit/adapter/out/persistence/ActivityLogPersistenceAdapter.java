package dev.curriculotron.audit.adapter.out.persistence;

import dev.curriculotron.audit.application.port.out.ActivityLogRepository;
import dev.curriculotron.audit.domain.ActivityRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
class ActivityLogPersistenceAdapter implements ActivityLogRepository {

  private final ActivityLogJpaRepository jpa;

  ActivityLogPersistenceAdapter(ActivityLogJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void append(ActivityRecord record) {
    jpa.save(ActivityLogEntity.from(record));
  }

  @Override
  public List<ActivityRecord> since(Instant since, int limit) {
    return jpa.findByAtGreaterThanEqualOrderByAtDescIdDesc(since, Limit.of(limit)).stream()
        .map(ActivityLogEntity::toDomain)
        .toList();
  }

  @Override
  public List<ActivityRecord> forTaskRun(String taskRunId) {
    return jpa.findByTaskRunIdOrderByAtAscIdAsc(taskRunId).stream()
        .map(ActivityLogEntity::toDomain)
        .toList();
  }
}
