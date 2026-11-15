package dev.curriculotron.sources;

import dev.curriculotron.orchestration.application.RoutineScheduler;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
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
  private final EntityManager em;

  SearchRoutineBootstrap(RoutineRepository routines, RoutineScheduler scheduler, EntityManager em) {
    this.routines = routines;
    this.scheduler = scheduler;
    this.em = em;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (routines.findAll().stream().anyMatch(r -> r.purpose().equals("search"))) return;
    var time =
        em.createNativeQuery(
                "SELECT daily_search_time_local FROM search_criteria WHERE id=1", String.class)
            .getResultList();
    String routineId =
        scheduler.scheduleDaily(
            "search",
            null,
            time.isEmpty() ? LocalTime.of(7, 30) : LocalTime.parse((String) time.get(0)));
    scheduler.setEnabled(routineId, false);
  }
}
