package dev.curriculotron.sources;

import dev.curriculotron.orchestration.application.RoutineScheduler;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.shared.web.ApiException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/search-routine")
public class SearchRoutineController {
  private final RoutineRepository routines;
  private final RoutineScheduler scheduler;

  public SearchRoutineController(RoutineRepository routines, RoutineScheduler scheduler) {
    this.routines = routines;
    this.scheduler = scheduler;
  }

  public record Setting(boolean enabled) {}

  @GetMapping
  public Setting get() {
    return new Setting(
        routines.findAll().stream().anyMatch(r -> r.purpose().equals("search") && r.enabled()));
  }

  @PutMapping
  public Setting update(@RequestBody Setting setting) {
    var routine =
        routines.findAll().stream()
            .filter(r -> r.purpose().equals("search"))
            .findFirst()
            .orElseThrow(() ->
                ApiException.conflict(
                    "scheduler_disabled", "O agendador está desabilitado nesta instalação."));
    scheduler.setEnabled(routine.id(), setting.enabled());
    return get();
  }
}
