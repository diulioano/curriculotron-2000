package dev.curriculotron.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** T007 — NFR-001: sem sessão ⇒ 401 sem dados; mutação sem X-CSRF-Token ⇒ 403. */
class SecurityIntegrationTest extends AppTestBase {

  @Autowired MockMvc mvc;

  @BeforeEach
  void ensureSetup() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD); // 409 nas repetições; tudo bem
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/home",
        "/api/opportunities",
        "/api/applications",
        "/api/activity",
        "/api/profile",
        "/api/bots",
        "/api/data-inventory",
        "/api/rota-que-nao-existe"
      })
  void rotasPrivadasSemSessaoRetornam401SemDadosPrivados(String path) throws Exception {
    MvcResult r =
        mvc.perform(get(path))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("unauthorized"))
            .andReturn();

    String body = r.getResponse().getContentAsString();
    // Somente o envelope de erro: nenhuma outra chave, nenhum dado.
    assertThat(body).startsWith("{\"error\":{").doesNotContain("password").doesNotContain("token");
    assertThat(r.getResponse().getHeader("Set-Cookie")).isNull();
  }

  @Test
  void mutacaoSemCsrfRetorna403MesmoSemSessao() throws Exception {
    mvc.perform(post("/api/searches").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("forbidden"));
  }

  @Test
  void mutacaoAutenticadaSemCsrfRetorna403() throws Exception {
    TestSession s = TestSession.login(mvc, TestSession.PASSWORD);

    mvc.perform(
            post("/api/searches")
                .session(s.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/criteria")
                .session(s.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/session").session(s.session())).andExpect(status().isForbidden());
  }

  @Test
  void mutacaoComCsrfErradoRetorna403() throws Exception {
    TestSession s = TestSession.login(mvc, TestSession.PASSWORD);
    mvc.perform(
            post("/api/searches")
                .session(s.session())
                .header("X-CSRF-Token", "token-falso")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void comSessaoECsrfRotaInexistenteDa404EmJson() throws Exception {
    TestSession s = TestSession.login(mvc, TestSession.PASSWORD);
    mvc.perform(get("/api/rota-que-nao-existe").session(s.session()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"));
  }

  @Test
  void saudeESessaoSaoPublicasSemDadosPrivados() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
    mvc.perform(get("/api/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.setupRequired").value(false));
  }

  @Test
  void respostasTrazemCabecalhosDeSeguranca() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().string("X-Frame-Options", "DENY"));
  }
}
