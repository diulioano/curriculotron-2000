package dev.curriculotron.salary.application;

import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.domain.*;
import dev.curriculotron.salary.adapter.out.persistence.SalaryEstimatePersistenceAdapter;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class SalaryRefreshService implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(SalaryRefreshService.class);

  public record RefreshResult(String taskRunId, TaskRunState state) {}

  private final SalaryEstimateService salary;
  private final TaskRunService tasks;
  private final SalaryLookupCoordinator coordinator;
  private final SalaryEstimatePersistenceAdapter store;
  private final WriteExecutor writer;
  private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

  public SalaryRefreshService(
      SalaryEstimateService salary,
      TaskRunService tasks,
      SalaryLookupCoordinator coordinator,
      SalaryEstimatePersistenceAdapter store,
      WriteExecutor writer) {
    this.salary = salary;
    this.tasks = tasks;
    this.coordinator = coordinator;
    this.store = store;
    this.writer = writer;
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(16);
    executor.setThreadNamePrefix("salary-refresh-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(40);
    executor.initialize();
  }

  // Short reservation only: no source access or waiting for lookups under this monitor.
  public synchronized RefreshResult refresh(Opportunity job, String key) {
    var remembered = store.refreshTask(job.id(), key);
    if (remembered.isPresent()) {
      var task = tasks.find(remembered.get()).orElseThrow();
      return new RefreshResult(task.id(), task.state());
    }
    String scopedKey = "salary:" + job.id() + ":" + key;
    var replay = tasks.findByIdempotencyKey(scopedKey);
    if (replay.isPresent()) return new RefreshResult(replay.get().id(), replay.get().state());
    String profile = SalaryEstimateService.profile(job).key();
    var claim =
        coordinator.reserve(
            profile,
            () ->
                tasks
                    .createOrGet(NewTaskRun.system("salary", TaskRunTrigger.manual, scopedKey))
                    .run()
                    .id());
    var lookup = claim.lookup();
    try {
      writer.run(() -> store.rememberRefresh(job.id(), key, lookup.task()));
    } catch (RuntimeException error) {
      if (claim.owner()) {
        lookup.result().completeExceptionally(error);
        coordinator.release(profile, lookup);
        tasks.fail(lookup.task(), "Não foi possível registrar a atualização salarial.");
      }
      throw error;
    }
    if (claim.owner()) {
      try {
        executor.execute(() -> run(job, lookup));
      } catch (RejectedExecutionException error) {
        lookup.result().completeExceptionally(error);
        coordinator.release(profile, lookup);
        tasks.fail(lookup.task(), "Capacidade de consultas salariais esgotada. Tente novamente.");
      }
    }
    return new RefreshResult(lookup.task(), tasks.find(lookup.task()).orElseThrow().state());
  }

  private void run(Opportunity job, SalaryLookupCoordinator.Lookup lookup) {
    try {
      tasks.start(lookup.task());
      String result = salary.enrichReserved(job, lookup, true);
      if ("succeeded".equals(result)) tasks.complete(lookup.task());
      else
        tasks.pause(
            lookup.task(), PauseReason.needs_person, "Pesquisa assistida necessária: " + result);
    } catch (Exception error) {
      lookup.result().completeExceptionally(error);
      log.error(
          "Falha na atualização salarial taskRunId={} tipo={}",
          lookup.task(),
          error.getClass().getName());
      try {
        tasks.fail(lookup.task(), "Não foi possível atualizar a referência salarial.");
      } catch (RuntimeException persistenceError) {
        log.error(
            "Falha ao registrar estado salarial taskRunId={} tipo={}",
            lookup.task(),
            persistenceError.getClass().getName());
      }
    } finally {
      coordinator.release(SalaryEstimateService.profile(job).key(), lookup);
    }
  }

  @Override
  public void close() {
    executor.shutdown();
  }
}
