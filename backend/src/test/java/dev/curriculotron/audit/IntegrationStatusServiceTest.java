package dev.curriculotron.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.audit.application.IntegrationStatusService;
import dev.curriculotron.audit.domain.IntegrationState;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.support.AppTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T009 — FR-017: falha atualiza só last_attempt_at, nunca last_success_at. */
class IntegrationStatusServiceTest extends AppTestBase {

  @Autowired IntegrationStatusService service;
  @Autowired AppClock clock;

  private static final Instant T0 = Instant.parse("2026-11-14T10:00:00Z");

  @AfterEach
  void resetClock() {
    clock.setClock(Clock.system(AppClock.ZONE));
  }

  private void at(Instant t) {
    clock.setClock(Clock.fixed(t, ZoneOffset.UTC));
  }

  @Test
  void falhaDepoisDeSucessoNaoMudaLastSuccess() {
    at(T0);
    service.recordSuccess("source:gupy", "12 vagas");
    at(T0.plusSeconds(3600));
    service.recordFailure("source:gupy", IntegrationState.degraded, "HTTP 503");

    var s = service.find("source:gupy").orElseThrow();
    assertThat(s.lastSuccessAt()).isEqualTo(T0);
    assertThat(s.lastAttemptAt()).isEqualTo(T0.plusSeconds(3600));
    assertThat(s.state()).isEqualTo(IntegrationState.degraded);
    assertThat(s.detail()).isEqualTo("HTTP 503");
  }

  @Test
  void falhaSemSucessoAnteriorDeixaLastSuccessNulo() {
    at(T0);
    service.recordFailure("gmail", IntegrationState.disconnected, "autorização expirada");

    var s = service.find("gmail").orElseThrow();
    assertThat(s.lastSuccessAt()).isNull();
    assertThat(s.lastAttemptAt()).isEqualTo(T0);
  }

  @Test
  void sucessoDepoisDeFalhaAtualizaAmbos() {
    at(T0);
    service.recordFailure("openbot", IntegrationState.disconnected, "OpenBot fechado");
    at(T0.plusSeconds(60));
    service.recordSuccess("openbot", null);

    var s = service.find("openbot").orElseThrow();
    assertThat(s.lastSuccessAt()).isEqualTo(T0.plusSeconds(60));
    assertThat(s.lastAttemptAt()).isEqualTo(T0.plusSeconds(60));
    assertThat(s.state()).isEqualTo(IntegrationState.ok);
  }

  @Test
  void falhaNaoPodeMarcarEstadoOk() {
    assertThatThrownBy(() -> service.recordFailure("openbot", IntegrationState.ok, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
