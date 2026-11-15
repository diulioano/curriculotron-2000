package dev.curriculotron.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * Relógio injetável. Instantes são UTC ({@code *_at}); datas de calendário usam {@code
 * America/Sao_Paulo} ({@code *_local_date}), base do teto diário de 10 seleções.
 */
@Component
public class AppClock {

  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  private volatile Clock clock;

  public AppClock() {
    this(Clock.system(ZONE));
  }

  public AppClock(Clock clock) {
    this.clock = clock.withZone(ZONE);
  }

  public Instant now() {
    return clock.instant();
  }

  public LocalDate localDate() {
    return LocalDate.now(clock);
  }

  public ZonedDateTime localNow() {
    return ZonedDateTime.now(clock);
  }

  public LocalDate localDateOf(Instant instant) {
    return instant.atZone(ZONE).toLocalDate();
  }

  /** Só para testes: troca o relógio em tempo de execução. */
  public void setClock(Clock clock) {
    this.clock = clock.withZone(ZONE);
  }
}
