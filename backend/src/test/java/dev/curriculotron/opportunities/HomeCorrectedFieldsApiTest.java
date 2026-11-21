package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.OpportunityIngestionService;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.sources.JobSource;
import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato {@code correctedFields} nos cartões de {@code GET /api/home}. Classe própria porque as
 * correções são append-only e o {@code HomeApiTest} apaga vagas entre os testes.
 */
class HomeCorrectedFieldsApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityIngestionService ingestion;
  @Autowired DailySelectionService selections;
  @Autowired AppClock clock;

  @BeforeEach
  void fixClock() {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
  }

  private String ingestJava(String externalId, String title, String company) {
    return ingestion.ingest(
        new JobSource.Item(
            "lever:ciandt",
            externalId,
            "https://jobs.lever.co/ciandt/" + externalId,
            "Requisitos: Java e Spring Boot\nRemoto",
            title,
            "Remoto"),
        company);
  }

  @Test
  void exposesCorrectedFieldsForEachSelectedCardAndEmptyListWhenUncorrected() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    String corrected = ingestJava("home-corrected", "Desenvolvedor Java", "CI&T");
    String untouched = ingestJava("home-untouched", "Desenvolvedor Backend Java", "Itaú");
    selections.selectToday();
    mvc.perform(
            post("/api/opportunities/" + corrected + "/corrections")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"Canoas, RS\",\"workMode\":\"hybrid\"}"))
        .andExpect(status().isOk());

    String json =
        mvc.perform(get("/api/home").session(s.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<List<String>>read(json, "$.selectionToday.items[*].id"))
        .contains(corrected, untouched);
    assertThat(
            JsonPath.<List<List<String>>>read(
                json, "$.selectionToday.items[?(@.id=='" + corrected + "')].correctedFields"))
        .containsExactly(List.of("location", "work_mode"));
    assertThat(
            JsonPath.<List<List<String>>>read(
                json, "$.selectionToday.items[?(@.id=='" + untouched + "')].correctedFields"))
        .containsExactly(List.of());
  }
}
