package dev.curriculotron.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.audit.domain.ActivityResult;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Migrações, sementes (T020), ActivityLog append-only (T016) e Idempotency-Key (T015). */
class FoundationIntegrationTest extends AppTestBase {

  @Autowired JdbcClient jdbc;
  @Autowired WriteExecutor writer;
  @Autowired ActivityLogService activity;
  @Autowired MockMvc mvc;

  @Test
  void sqliteEmWal() {
    String mode = jdbc.sql("PRAGMA journal_mode").query(String.class).single();
    assertThat(mode).isEqualToIgnoringCase("wal");
    assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
  }

  @Test
  void criteriosPadraoSemeados() {
    Map<String, Object> c =
        jdbc.sql("SELECT * FROM search_criteria WHERE id = 1").query().singleRow();
    assertThat(c.get("clt_min_monthly_brl")).isEqualTo(6000);
    assertThat(c.get("pj_min_monthly_brl")).isEqualTo(9000);
    assertThat((String) c.get("hybrid_regions")).contains("RM Porto Alegre");
    assertThat(c.get("remote_preferred")).isEqualTo(1);
    assertThat(c.get("deadline_highlight_days")).isEqualTo(7);
    assertThat(c.get("urgent_hours")).isEqualTo(48);
  }

  @Test
  void empresasPrioritariasSemeadasComNotaDaCwi() {
    List<String> names =
        jdbc.sql("SELECT name FROM company WHERE tier = 'priority' ORDER BY name")
            .query(String.class)
            .list();
    assertThat(names).containsExactlyInAnyOrder("PicPay", "BV", "CWI", "DB", "CI&T", "SAP");
    String note =
        jdbc.sql("SELECT notes FROM company WHERE name = 'CWI'").query(String.class).single();
    assertThat(note).isEqualTo("experiência prévia na CWI; interesse em retornar");
  }

  @Test
  void activityLogEAppendOnly() {
    String id = activity.record(ActivityEntry.user("teste", "x", "1"));

    assertThatThrownBy(() ->
            writer.run(() ->
                jdbc.sql("UPDATE activity_log SET action = 'x' WHERE id = ?").param(id).update()))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() ->
            writer.run(() -> jdbc.sql("DELETE FROM activity_log WHERE id = ?").param(id).update()))
        .hasMessageContaining("append-only");
  }

  @Test
  void instantesGravadosEmIsoUtcComMilissegundosFixos() {
    String id = activity.record(ActivityEntry.user("teste", "x", "2"));
    String at =
        jdbc.sql("SELECT at FROM activity_log WHERE id = ?").param(id).query(String.class).single();
    // Largura fixa: ordem de texto = ordem cronológica nas consultas por intervalo.
    assertThat(at).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
  }

  @Test
  void autorInvalidoERecusado() {
    assertThatThrownBy(
            () -> new ActivityEntry(null, "admin", "x", null, null, null, ActivityResult.ok, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void idempotencyKeyRepeteMesmaRespostaERecusaReusoEmOutraRota() throws Exception {
    TestSession s = TestSession.anonymous(mvc);
    var req =
        post("/api/setup")
            .session(s.session())
            .header("X-CSRF-Token", s.csrf())
            .header("Idempotency-Key", "setup-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(TestSession.json(TestSession.PASSWORD));

    mvc.perform(req).andExpect(status().isCreated());
    // Sem a chave seria 409; com a mesma chave devolve a resposta original.
    mvc.perform(req)
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotent-Replayed", "true"));

    TestSession logged = TestSession.login(mvc, TestSession.PASSWORD);
    mvc.perform(
            post("/api/searches")
                .session(logged.session())
                .header("X-CSRF-Token", logged.csrf())
                .header("Idempotency-Key", "setup-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));
  }

  @Test
  void requestsConcorrentesComMesmaChaveExecutamUmaVezEConflitamComOutroPayload() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    TestSession logged = TestSession.login(mvc, TestSession.PASSWORD);
    String title = "ConcorrÃªncia idempotente " + UUID.randomUUID();
    String key = "manual-race-" + UUID.randomUUID();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var request =
          (java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult>)
              () -> {
                start.await();
                return mvc.perform(
                        post("/api/opportunities/manual")
                            .session(logged.session())
                            .header("X-CSRF-Token", logged.csrf())
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"" + title + "\"}"))
                    .andReturn();
              };
      var first = executor.submit(request);
      var second = executor.submit(request);
      start.countDown();
      var firstResponse = first.get();
      var secondResponse = second.get();
      assertThat(firstResponse.getResponse().getStatus()).isEqualTo(200);
      assertThat(secondResponse.getResponse().getStatus()).isEqualTo(200);
      String firstId =
          com.jayway.jsonpath.JsonPath.read(
              firstResponse.getResponse().getContentAsString(), "$.id");
      String secondId =
          com.jayway.jsonpath.JsonPath.read(
              secondResponse.getResponse().getContentAsString(), "$.id");
      assertThat(secondId).isEqualTo(firstId);
      assertThat(
              jdbc.sql("SELECT count(*) FROM opportunity WHERE title = ?")
                  .param(title)
                  .query(Long.class)
                  .single())
          .isEqualTo(1L);

      mvc.perform(
              post("/api/opportunities/manual")
                  .session(logged.session())
                  .header("X-CSRF-Token", logged.csrf())
                  .header("Idempotency-Key", key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"text\":\"outro payload\"}"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));
    } finally {
      executor.shutdownNow();
    }
  }
}
