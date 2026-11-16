package dev.curriculotron.sources;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.audit.domain.IntegrationState;
import dev.curriculotron.opportunities.application.*;
import dev.curriculotron.orchestration.application.TaskRunService;
import dev.curriculotron.orchestration.application.port.out.RoutineHandler;
import dev.curriculotron.orchestration.domain.*;
import dev.curriculotron.shared.*;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Daily (routine) and on-demand search over the companies' configured career pages (FR-003). Each
 * source consulted or failed is recorded in the TaskRun and in {@code
 * IntegrationStatus(source:<id>)}; partial results never claim full coverage.
 */
@Service
public class SearchService implements RoutineHandler {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private dev.curriculotron.salary.application.SalaryEstimateService salary;

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  private final ConfiguredSourceRepository boards;
  @org.springframework.beans.factory.annotation.Autowired private PublicationStore publications;
  private final JobSourceFactory sources;
  private final TaskRunService tasks;
  private final IntegrationStatusService status;
  private final OpportunityIngestionService ingestion;
  private final DailySelectionService selections;
  private final AppClock clock;
  private final ThreadPoolTaskExecutor executor;

  public SearchService(
      ConfiguredSourceRepository boards,
      JobSourceFactory sources,
      TaskRunService tasks,
      IntegrationStatusService status,
      OpportunityIngestionService ingestion,
      DailySelectionService selections,
      AppClock clock,
      @org.springframework.beans.factory.annotation.Qualifier("searchTaskExecutor")
          ThreadPoolTaskExecutor executor) {
    this.boards = boards;
    this.sources = sources;
    this.tasks = tasks;
    this.status = status;
    this.ingestion = ingestion;
    this.selections = selections;
    this.clock = clock;
    this.executor = executor;
  }

  public String submit(String key, TaskRunTrigger trigger) {
    return submit(key, trigger, SearchWindow.resolve(null, null, clock.localDate()));
  }

  public String submit(String key, TaskRunTrigger trigger, SearchWindow window) {
    var created = tasks.createOrGet(NewTaskRun.system("search", trigger, key));
    if (created.created()) {
      try {
        executor.execute(() -> execute(created.run().id(), window));
      } catch (TaskRejectedException rejected) {
        tasks.fail(created.run().id(), "Capacidade de busca esgotada. Tente novamente.");
        log.warn("Busca rejeitada por capacidade taskRunId={}", created.run().id());
      }
    }
    return created.run().id();
  }

  void execute(String id) {
    execute(id, SearchWindow.resolve(null, null, clock.localDate()));
  }

  void execute(String id, SearchWindow window) {
    var eligible = new HashSet<String>();
    tasks.start(id);
    var consulted = new ArrayList<String>();
    var failed = new ArrayList<String>();
    try {
      var configured = boards.boards();
      if (configured.isEmpty()) {
        tasks.pause(
            id,
            PauseReason.source_unavailable,
            "Nenhuma página de carreira configurada; use entrada manual.");
        return;
      }
      for (var board : configured) {
        String source = board.adapter();
        consulted.add(source);
        try {
          var result = sources.forAdapter(source).fetch();
          if (!result.failures().isEmpty())
            throw new IllegalStateException(result.failures().getFirst().code());
          for (var item : result.items()) {
            if (item.url() == null || !window.includes(item.publishedAt())) continue;
            var ingested = ingestion.ingestResult(item, board.company());
            if (ingested.sourceConflict()) continue;
            String opportunityId = ingested.opportunityId();
            eligible.add(opportunityId);
            if (publications != null) publications.record(opportunityId, item.publishedAt());
          }
          status.recordSuccess("source:" + source, String.join(" ", result.limitations()));
        } catch (Exception e) {
          if (Thread.currentThread().isInterrupted()) {
            tasks.pause(
                id, PauseReason.source_unavailable, "Busca interrompida durante a consulta.");
            return;
          }
          log.warn("Fonte {} falhou: {}", source, e.getClass().getSimpleName());
          failed.add(source);
          status.recordFailure(
              "source:" + source,
              IntegrationState.degraded,
              "Fonte indisponível ou não suportada.");
        }
        tasks.recordSources(id, consulted, failed);
      }
      selections.selectMatchingToday(eligible::contains);
      if (salary != null) salary.enrichIds(eligible, id);
      if (failed.size() == consulted.size())
        tasks.pause(id, PauseReason.source_unavailable, "Nenhuma fonte respondeu.");
      else tasks.complete(id);
    } catch (Exception e) {
      log.warn("Busca falhou: {}", e.getClass().getSimpleName());
      tasks.fail(id, "Não foi possível concluir a busca.");
    }
  }

  public String purpose() {
    return "search";
  }

  public void run(Routine routine, LocalDate date, String key) {
    submit(key, TaskRunTrigger.routine);
  }
}
