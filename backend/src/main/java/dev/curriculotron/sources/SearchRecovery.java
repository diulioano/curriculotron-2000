package dev.curriculotron.sources;

import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SearchRecovery {
  private final TaskRunService tasks;
  private final WriteExecutor writer;

  SearchRecovery(TaskRunService tasks, WriteExecutor writer) {
    this.tasks = tasks;
    this.writer = writer;
  }

  @EventListener(ApplicationReadyEvent.class)
  void recover() {
    writer.run(() -> {
      for (var state : List.of(TaskRunState.queued, TaskRunState.running)) {
        for (var task : tasks.inState(state)) {
          if ("search".equals(task.kind())) {
            tasks.fail(task.id(), "Busca interrompida pelo reinício. Inicie uma nova busca.");
          }
        }
      }
    });
  }
}
