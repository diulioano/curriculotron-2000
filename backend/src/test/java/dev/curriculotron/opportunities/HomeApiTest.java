package dev.curriculotron.opportunities;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.curriculotron.opportunities.application.DailySelectionService;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityRepository.SourcedOpportunity;
import dev.curriculotron.shared.AppClock;
import dev.curriculotron.shared.persistence.WriteExecutor;
import dev.curriculotron.support.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** T047 — página inicial (FR-016, US5-4). */
class HomeApiTest extends AppTestBase {
  @Autowired MockMvc mvc;
  @Autowired OpportunityRepository jobs;
  @Autowired DailySelectionService selections;
  @Autowired WriteExecutor writer;
  @Autowired AppClock clock;

  @Test
  void reflectsRealDataOnlyAndNeverInventsFreshness() throws Exception {
    clock.setClock(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), AppClock.ZONE));
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataAsOf").value(nullValue()))
        .andExpect(jsonPath("$.selectionToday.localDate").value("2026-11-14"))
        .andExpect(jsonPath("$.selectionToday.count").value(0))
        .andExpect(jsonPath("$.selectionToday.limit").value(10))
        .andExpect(jsonPath("$.selectionToday.items").isEmpty())
        .andExpect(jsonPath("$.applicationsWithNextActions").isArray())
        .andExpect(jsonPath("$.mailNeedingAttention").isArray())
        .andExpect(jsonPath("$.reviewQueueCount").value(0))
        .andExpect(jsonPath("$.missedRoutines").isArray());

    writer.run(() ->
        jobs.createFromSource(
            new SourcedOpportunity(
                "Desenvolvedor Java",
                "CI&T",
                "Remoto",
                "remote",
                List.of("Java"),
                "strong",
                90,
                List.of("Java e Spring no perfil")),
            "2026-11-14T14:00:00Z"));
    selections.selectToday();
    for (String url : List.of("https://one.test/job", "https://two.test/job"))
      mvc.perform(
              post("/api/opportunities/manual")
                  .session(s.session())
                  .header("X-CSRF-Token", s.csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"url\":\"" + url + "\",\"text\":\"Java remoto\"}"))
          .andExpect(status().isOk());

    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(jsonPath("$.dataAsOf").value(notNullValue()))
        .andExpect(jsonPath("$.selectionToday.count").value(1))
        .andExpect(jsonPath("$.selectionToday.items[0].title").value("Desenvolvedor Java"))
        .andExpect(jsonPath("$.selectionToday.items[0].fitReasons[0]").exists())
        .andExpect(jsonPath("$.reviewQueueCount").value(1))
        .andExpect(jsonPath("$.integrations[?(@.id=='source:manual')].state").value(hasItem("ok")));
  }
}
