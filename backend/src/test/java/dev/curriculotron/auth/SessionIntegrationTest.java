package dev.curriculotron.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.shared.AppClock;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** T008 — setup só uma vez (409 depois); senha errada repetida ⇒ 429. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionIntegrationTest extends AppTestBase {

  @Autowired MockMvc mvc;
  @Autowired AppClock clock;

  @AfterEach
  void resetClock() {
    clock.setClock(Clock.system(AppClock.ZONE));
  }

  @Test
  @Order(1)
  void antesDoSetupLoginPedeSetupESessaoIndicaSetupPendente() throws Exception {
    mvc.perform(get("/api/session")).andExpect(jsonPath("$.setupRequired").value(true));
    assertThat(TestSession.attemptLogin(mvc, TestSession.PASSWORD).getResponse().getStatus())
        .isEqualTo(409);
  }

  @Test
  @Order(2)
  void senhaCurtaERecusada() throws Exception {
    TestSession s = TestSession.anonymous(mvc);
    mvc.perform(
            post("/api/setup")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestSession.json("curta")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("weak_password"));
  }

  @Test
  @Order(3)
  void setupFuncionaUmaVezE409Depois() throws Exception {
    TestSession s = TestSession.anonymous(mvc);
    mvc.perform(
            post("/api/setup")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestSession.json(TestSession.PASSWORD)))
        .andExpect(status().isCreated());

    TestSession s2 = TestSession.anonymous(mvc);
    mvc.perform(
            post("/api/setup")
                .session(s2.session())
                .header("X-CSRF-Token", s2.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestSession.json("outra-senha-qualquer")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("already_configured"));

    // A senha original continua valendo.
    TestSession.login(mvc, TestSession.PASSWORD);
  }

  @Test
  @Order(4)
  void loginCriaSessaoAutenticadaELogoutEncerra() throws Exception {
    TestSession s = TestSession.login(mvc, TestSession.PASSWORD);
    mvc.perform(get("/api/session").session(s.session()))
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.csrfToken").value(s.csrf()));

    mvc.perform(delete("/api/session").session(s.session()).header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/home").session(s.session())).andExpect(status().isUnauthorized());
  }

  @Test
  @Order(5)
  void senhaErradaRepetidaRetorna429EBloqueiaAteSenhaCerta() throws Exception {
    for (int i = 1; i <= 4; i++) {
      MvcResult r = TestSession.attemptLogin(mvc, "senha-errada-" + i);
      assertThat(r.getResponse().getStatus()).as("tentativa %d", i).isEqualTo(401);
    }
    MvcResult fifth = TestSession.attemptLogin(mvc, "senha-errada-5");
    assertThat(fifth.getResponse().getStatus()).isEqualTo(429);
    assertThat(fifth.getResponse().getHeader("Retry-After")).isNotNull();

    // Bloqueado: nem a senha certa entra.
    assertThat(TestSession.attemptLogin(mvc, TestSession.PASSWORD).getResponse().getStatus())
        .isEqualTo(429);

    // Depois do bloqueio, a senha certa volta a funcionar.
    clock.setClock(Clock.offset(Clock.system(AppClock.ZONE), Duration.ofMinutes(6)));
    TestSession.login(mvc, TestSession.PASSWORD);
  }
}
