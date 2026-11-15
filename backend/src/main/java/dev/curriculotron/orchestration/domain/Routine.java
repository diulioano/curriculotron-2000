package dev.curriculotron.orchestration.domain;

import dev.curriculotron.shared.AppClock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Rotina agendada pelo backend (D-10), em horário local {@code America/Sao_Paulo}. */
public record Routine(
    String id,
    String purpose,
    String ownerBotId,
    RoutineKind kind,
    LocalTime timeLocal,
    boolean enabled,
    Instant nextRunAt,
    Instant lastRunAt) {

  /** Nova rotina diária cujo primeiro disparo é a próxima ocorrência do horário. */
  public static Routine daily(
      String id, String purpose, String ownerBotId, LocalTime time, Instant now) {
    return new Routine(
        id, purpose, ownerBotId, RoutineKind.daily, time, true, nextDaily(time, now), null);
  }

  /** Próxima ocorrência diária estritamente depois de {@code after}. */
  public static Instant nextDaily(LocalTime time, Instant after) {
    ZonedDateTime local = after.atZone(AppClock.ZONE);
    ZonedDateTime candidate = local.toLocalDate().atTime(time).atZone(AppClock.ZONE);
    if (!candidate.toInstant().isAfter(after)) {
      candidate = candidate.toLocalDate().plusDays(1).atTime(time).atZone(AppClock.ZONE);
    }
    return candidate.toInstant();
  }

  /** Ocorrências vencidas até {@code now}, da mais antiga à mais recente (vazia se nada venceu). */
  public List<Instant> dueOccurrences(Instant now) {
    List<Instant> occurrences = new ArrayList<>();
    Instant occ = nextRunAt;
    while (!occ.isAfter(now)) {
      occurrences.add(occ);
      occ = nextDaily(timeLocal, occ);
    }
    return occurrences;
  }

  /** Marca execução em {@code ranAt} e agenda a próxima ocorrência futura. */
  public Routine ranAt(Instant ranAt) {
    return new Routine(
        id, purpose, ownerBotId, kind, timeLocal, enabled, nextDaily(timeLocal, ranAt), ranAt);
  }

  public Routine withEnabled(boolean value) {
    return new Routine(id, purpose, ownerBotId, kind, timeLocal, value, nextRunAt, lastRunAt);
  }
}
