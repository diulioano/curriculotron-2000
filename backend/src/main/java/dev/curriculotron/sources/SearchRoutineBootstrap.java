package dev.curriculotron.sources;

import dev.curriculotron.orchestration.application.RoutineScheduler;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Ensures the daily search routine exists at {@code search_criteria.daily_search_time_local}. */
@Component
@ConditionalOnProperty(
    name = "curriculotron.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
class SearchRoutineBootstrap implements ApplicationRunner {
  private final RoutineRepository routines;
  private final RoutineScheduler scheduler;
  private final SearchCriteriaRepository criteria;

  SearchRoutineBootstrap(
      RoutineRepository routines, RoutineScheduler scheduler, SearchCriteriaRepository criteria) {
    this.routines = routines;
    this.scheduler = scheduler;
    this.criteria = criteria;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (routines.findAll().stream().anyMatch(r -> r.purpose().equals("search"))) return;
    String routineId =
        scheduler.scheduleDaily("search", null, criteria.current().dailySearchTime());
    scheduler.setEnabled(routineId, false);
  }
}
