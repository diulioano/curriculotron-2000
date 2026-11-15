package dev.curriculotron.orchestration.application;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.audit.domain.ActivityResult;
import dev.curriculotron.orchestration.application.port.out.RoutineHandler;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.orchestration.domain.Routine;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agendador de rotinas (D-10): um passo por tick, horário local {@code America/Sao_Paulo}.
 *
 * <p>Se o computador ficou desligado e várias ocorrências passaram, elas são colapsadas em uma
 * única execução (a mais recente) e as anteriores ficam registradas como não executadas — exibidas
 * na Início. O {@code next_run_at} avança antes de chamar o handler; o handler é idempotente pela
 * chave {@code routine:<id>:<data local>}, e a retomada de tarefas interrompidas é feita pelo
 * TaskRun.
 */
@Service
public class RoutineScheduler {

  private static final Logger log = LoggerFactory.getLogger(RoutineScheduler.class);

  private final RoutineRepository routines;
  private final WriteExecutor writer;
  private final AppClock clock;
  private final ActivityLogService activity;
  private final Map<String, RoutineHandler> handlers;

  public RoutineScheduler(
      RoutineRepository routines,
      WriteExecutor writer,
      AppClock clock,
      ActivityLogService activity,
      List<RoutineHandler> handlers) {
    this.routines = routines;
    this.writer = writer;
    this.clock = clock;
    this.activity = activity;
    this.handlers =
        handlers.stream().collect(Collectors.toMap(RoutineHandler::purpose, Function.identity()));
  }

  /** Cria uma rotina diária cujo primeiro disparo é a próxima ocorrência do horário. */
  public String scheduleDaily(String purpose, String ownerBotId, LocalTime time) {
    Routine routine = Routine.daily(Ids.newId(), purpose, ownerBotId, time, clock.now());
    writer.run(() -> routines.save(routine));
    return routine.id();
  }

  public void setEnabled(String routineId, boolean enabled) {
    writer.run(
        () -> routines.find(routineId).ifPresent(r -> routines.save(r.withEnabled(enabled))));
  }

  /** Um passo do agendador. Retorna quantas rotinas foram disparadas. */
  public int tick() {
    Instant now = clock.now();
    int fired = 0;
    for (Routine r : routines.due(now)) {
      Instant executed = collapse(r, now);
      fired++;
      dispatch(r, executed);
    }
    return fired;
  }

  /** Avança a rotina e registra ocorrências perdidas. Retorna a ocorrência que será executada. */
  private Instant collapse(Routine r, Instant now) {
    List<Instant> occurrences = r.dueOccurrences(now);
    Instant executed = occurrences.getLast();
    List<Instant> missed = occurrences.subList(0, occurrences.size() - 1);

    writer.run(() -> {
      for (Instant m : missed) {
        routines.recordMissed(r.id(), m);
        activity.record(
            ActivityEntry.system(
                "routine.missed",
                "routine",
                r.id(),
                ActivityResult.skipped,
                "não executada em " + m + " (computador desligado ou app fechado)"));
      }
      routines.save(r.ranAt(now));
    });
    return executed;
  }

  private void dispatch(Routine r, Instant occurrence) {
    LocalDate localDate = clock.localDateOf(occurrence);
    String key = "routine:" + r.id() + ":" + localDate;
    RoutineHandler handler = handlers.get(r.purpose());
    if (handler == null) {
      activity.record(
          ActivityEntry.system(
              "routine.no_handler",
              "routine",
              r.id(),
              ActivityResult.skipped,
              "sem executor para '" + r.purpose() + "'"));
      return;
    }
    try {
      handler.run(r, localDate, key);
    } catch (RuntimeException e) {
      log.warn("Rotina {} falhou: {}", r.purpose(), e.getClass().getSimpleName());
      activity.record(
          ActivityEntry.system(
              "routine.failed",
              "routine",
              r.id(),
              ActivityResult.failed,
              e.getClass().getSimpleName()));
    }
  }
}
