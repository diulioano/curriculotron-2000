package dev.curriculotron.sources;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.audit.domain.IntegrationState;
import dev.curriculotron.opportunities.application.*;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository.SourcedOpportunity;
import dev.curriculotron.opportunities.domain.JobFactExtractor;
import dev.curriculotron.opportunities.domain.ObjectiveFilter;
import dev.curriculotron.opportunities.domain.RuleBasedRanker;
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
  private final SearchCriteriaRepository criteria;
  private final JobSourceFactory sources;
  private final TaskRunService tasks;
  private final IntegrationStatusService status;
  private final DedupeService dedupe;
  private final OpportunityRepository jobs;
  private final DailySelectionService selections;
  private final AppClock clock;
  private final JobFactExtractor extractor = new JobFactExtractor();
  private final ObjectiveFilter filter = new ObjectiveFilter();
  private final RuleBasedRanker ranker = new RuleBasedRanker();
  private final ThreadPoolTaskExecutor executor;

  public SearchService(
      ConfiguredSourceRepository boards,
      SearchCriteriaRepository criteria,
      JobSourceFactory sources,
      TaskRunService tasks,
      IntegrationStatusService status,
      DedupeService dedupe,
      OpportunityRepository jobs,
      DailySelectionService selections,
      AppClock clock,
      @org.springframework.beans.factory.annotation.Qualifier("searchTaskExecutor")
          ThreadPoolTaskExecutor executor) {
    this.boards = boards;
    this.criteria = criteria;
    this.sources = sources;
    this.tasks = tasks;
    this.status = status;
    this.dedupe = dedupe;
    this.jobs = jobs;
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
      var settings = criteria.current();
      for (var board : configured) {
        String source = board.adapter();
        consulted.add(source);
        try {
          var result = sources.forAdapter(source).fetch();
          if (!result.failures().isEmpty())
            throw new IllegalStateException(result.failures().getFirst().code());
          for (var item : result.items()) {
            if (item.url() == null || !window.includes(item.publishedAt())) continue;
            String opportunityId =
                dedupe.ingest(
                    item.source(),
                    item.externalId(),
                    item.url(),
                    excerpt(item.text()),
                    () ->
                        jobs.createFromSource(
                            rank(item, board.company(), board.juniorJustification(), settings),
                            clock.now().toString()));
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

  SourcedOpportunity rank(
      JobSource.Item item,
      String company,
      String juniorJustification,
      SearchCriteriaRepository.Settings s) {
    var c = s.criteria();
    var facts =
        extractor.extract(
            item.title(), company, item.location(), item.text(), c.stack(), c.hybridRegions());
    var salary = filter.evaluate("unknown", "unknown", null, null, s.cltMinimum(), s.pjMinimum());
    String role =
        facts.roleCompatible() && !c.roles().isEmpty()
            ? new TreeSet<>(c.roles()).first()
            : facts.title();
    var ranked =
        ranker.rank(
            new RuleBasedRanker.Input(
                role,
                company,
                facts.workMode(),
                facts.region(),
                facts.seniority(),
                juniorJustification,
                facts.stackFacts(),
                salary),
            c);
    return new SourcedOpportunity(
        facts.title(),
        company,
        facts.location(),
        facts.workMode(),
        JobFactExtractor.factList(facts),
        ranked.fit(),
        ranked.score(),
        ranked.reasons());
  }

  private static String excerpt(String text) {
    if (text == null) return null;
    return text.length() > 4000 ? text.substring(0, 4000) : text;
  }

  public String purpose() {
    return "search";
  }

  public void run(Routine routine, LocalDate date, String key) {
    submit(key, TaskRunTrigger.routine);
  }
}
