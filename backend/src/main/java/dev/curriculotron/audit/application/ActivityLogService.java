package dev.curriculotron.audit.application;

import dev.curriculotron.audit.application.port.out.ActivityLogRepository;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.audit.domain.ActivityRecord;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Registro de atividades append-only (NFR-007). Não há operação de alteração ou exclusão, e
 * triggers no banco recusam UPDATE/DELETE.
 */
@Service
public class ActivityLogService {

  private final ActivityLogRepository repository;
  private final WriteExecutor writer;
  private final AppClock clock;

  public ActivityLogService(
      ActivityLogRepository repository, WriteExecutor writer, AppClock clock) {
    this.repository = repository;
    this.writer = writer;
    this.clock = clock;
  }

  /** Grava a entrada (na transação de escrita atual, se houver) e devolve o id. */
  public String record(ActivityEntry entry) {
    ActivityRecord record = ActivityRecord.of(Ids.newId(), entry, clock.now());
    writer.run(() -> repository.append(record));
    return record.id();
  }

  public List<ActivityRecord> since(Instant since, int limit) {
    return repository.since(since, limit);
  }

  public List<ActivityRecord> forTaskRun(String taskRunId) {
    return repository.forTaskRun(taskRunId);
  }
}
