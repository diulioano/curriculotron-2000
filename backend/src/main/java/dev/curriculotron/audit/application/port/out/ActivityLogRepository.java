package dev.curriculotron.audit.application.port.out;

import dev.curriculotron.audit.domain.ActivityRecord;
import java.time.Instant;
import java.util.List;

/** Porta de saída do ActivityLog: só inclusão e leitura (append-only, NFR-007). */
public interface ActivityLogRepository {

  void append(ActivityRecord record);

  /** Mais recentes primeiro. */
  List<ActivityRecord> since(Instant since, int limit);

  /** Em ordem cronológica. */
  List<ActivityRecord> forTaskRun(String taskRunId);
}
