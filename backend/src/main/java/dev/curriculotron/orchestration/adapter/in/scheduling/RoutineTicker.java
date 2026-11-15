package dev.curriculotron.orchestration.adapter.in.scheduling;

import dev.curriculotron.orchestration.application.RoutineScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tick periódico do agendador (1 minuto); desligável em testes com {@code
 * curriculotron.scheduler.enabled=false}.
 */
@Component
@ConditionalOnProperty(
    name = "curriculotron.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
class RoutineTicker {

  private final RoutineScheduler scheduler;

  RoutineTicker(RoutineScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Scheduled(initialDelay = 5_000, fixedDelayString = "${curriculotron.scheduler.tick-ms:60000}")
  void tick() {
    scheduler.tick();
  }
}
