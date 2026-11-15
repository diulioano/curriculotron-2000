package dev.curriculotron.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.curriculotron.orchestration.application.RoutineScheduler;
import dev.curriculotron.orchestration.application.port.out.RoutineHandler;
import dev.curriculotron.orchestration.application.port.out.RoutineRepository;
import dev.curriculotron.orchestration.domain.MissedRoutine;
import dev.curriculotron.orchestration.domain.Routine;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.support.AppTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** T022 — tick, horário local, colapso de ocorrências perdidas. */
@Import(RoutineSchedulerTest.Handlers.class)
class RoutineSchedulerTest extends AppTestBase {

  record Call(String routineId, LocalDate localDate, String key) {}

  static final List<Call> CALLS = new CopyOnWriteArrayList<>();

  @TestConfiguration
  static class Handlers {
    @Bean
    RoutineHandler testHandler() {
      return new RoutineHandler() {
        @Override
        public String purpose() {
          return "test";
        }

        @Override
        public void run(Routine r, LocalDate localDate, String key) {
          CALLS.add(new Call(r.id(), localDate, key));
        }
      };
    }
  }

  @Autowired RoutineScheduler scheduler;
  @Autowired RoutineRepository routines;
  @Autowired AppClock clock;

  @BeforeEach
  void clear() {
    CALLS.clear();
  }

  @AfterEach
  void resetClock() {
    clock.setClock(Clock.system(AppClock.ZONE));
  }

  private void at(String iso) {
    clock.setClock(Clock.fixed(Instant.parse(iso), ZoneOffset.UTC));
  }

  @Test
  void proximaOcorrenciaUsaHorarioDeSaoPaulo() {
    // 07:30 em São Paulo (UTC-3) = 10:30Z
    assertThat(Routine.nextDaily(LocalTime.of(7, 30), Instant.parse("2026-11-14T09:00:00Z")))
        .isEqualTo(Instant.parse("2026-11-14T10:30:00Z"));
    assertThat(Routine.nextDaily(LocalTime.of(7, 30), Instant.parse("2026-11-14T10:30:00Z")))
        .isEqualTo(Instant.parse("2026-11-15T10:30:00Z"));
  }

  @Test
  void naoDisparaAntesDoHorarioEDisparaUmaVezNoHorario() {
    at("2026-11-14T09:00:00Z"); // 06:00 local
    String id = scheduler.scheduleDaily("test", null, LocalTime.of(7, 30));

    at("2026-11-14T10:29:00Z");
    assertThat(scheduler.tick()).isZero();

    at("2026-11-14T10:31:00Z");
    assertThat(scheduler.tick()).isEqualTo(1);
    assertThat(scheduler.tick()).isZero(); // mesmo minuto, não repete

    assertThat(CALLS)
        .containsExactly(new Call(id, LocalDate.of(2026, 11, 14), "routine:" + id + ":2026-11-14"));
    assertThat(routines.find(id).orElseThrow().nextRunAt())
        .isEqualTo(Instant.parse("2026-11-15T10:30:00Z"));
  }

  @Test
  void computadorDesligadoColapsaOcorrenciasERegistraAsPerdidas() {
    at("2026-11-14T09:00:00Z");
    String id = scheduler.scheduleDaily("test", null, LocalTime.of(7, 30));

    // Ligado de novo 3 dias depois, às 08:00 local.
    at("2026-11-17T11:00:00Z");
    assertThat(scheduler.tick()).isEqualTo(1);

    assertThat(CALLS).hasSize(1);
    assertThat(CALLS.getFirst().localDate()).isEqualTo(LocalDate.of(2026, 11, 17));

    var missed =
        routines.unacknowledgedMissed().stream().filter(m -> m.routineId().equals(id)).toList();
    assertThat(missed)
        .extracting(MissedRoutine::scheduledFor)
        .containsExactly(
            Instant.parse("2026-11-14T10:30:00Z"),
            Instant.parse("2026-11-15T10:30:00Z"),
            Instant.parse("2026-11-16T10:30:00Z"));
    assertThat(routines.find(id).orElseThrow().nextRunAt())
        .isEqualTo(Instant.parse("2026-11-18T10:30:00Z"));
  }

  @Test
  void rotinaSemExecutorAvancaSemFalhar() {
    at("2026-11-14T09:00:00Z");
    String id = scheduler.scheduleDaily("desconhecido", null, LocalTime.of(7, 30));
    at("2026-11-14T10:31:00Z");
    assertThat(scheduler.tick()).isEqualTo(1);
    assertThat(CALLS).isEmpty();
    assertThat(routines.find(id).orElseThrow().lastRunAt()).isNotNull();
  }
}
