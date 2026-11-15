package dev.curriculotron.salary.adapter.out.persistence;

import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.TaskRunState;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SalaryRecovery {
  private final SalaryEstimatePersistenceAdapter store;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final TaskRunService tasks;

  SalaryRecovery(
      SalaryEstimatePersistenceAdapter store,
      WriteExecutor writer,
      AppClock clock,
      TaskRunService tasks) {
    this.store = store;
    this.writer = writer;
    this.clock = clock;
    this.tasks = tasks;
  }

  @EventListener(ApplicationReadyEvent.class)
  void recover() {
    writer.run(() -> {
      store.interruptRunningAttempts(clock.now().toString());
      for (var state : java.util.List.of(TaskRunState.queued, TaskRunState.running)) {
        for (var task : tasks.inState(state)) {
          if ("salary".equals(task.kind())) {
            tasks.fail(
                task.id(), "Consulta interrompida pelo reinício. Inicie uma nova atualização.");
          }
        }
      }
    });
  }
}
