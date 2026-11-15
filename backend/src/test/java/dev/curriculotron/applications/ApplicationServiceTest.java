package dev.curriculotron.applications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ApplicationServiceTest extends AppTestBase {
  @Autowired MockMvc mvc;

  @Test
  void manualConfirmationHistoryAndDeadlines() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    var created =
        mvc.perform(
                post("/api/opportunities/manual")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Java remoto\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String job = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    mvc.perform(get("/api/opportunities/" + job).session(s.session())).andExpect(status().isOk());
    mvc.perform(get("/api/applications").session(s.session()))
        .andExpect(jsonPath("$.items").isEmpty());
    var applied =
        mvc.perform(
                post("/api/opportunities/" + job + "/applied")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationOrigin").value("manual"))
            .andExpect(jsonPath("$.status").value("submitted_awaiting"))
            .andReturn();
    String id = JsonPath.read(applied.getResponse().getContentAsString(), "$.id");
    mvc.perform(
            post("/api/opportunities/" + job + "/applied")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/opportunities/" + job + "/restore")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/applications/" + id + "/status")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"interview\",\"reason\":\"Convite recebido\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history.length()").value(2));
    mvc.perform(
            post("/api/applications/" + id + "/next-action")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"text\":\"Preparar entrevista\",\"dueAt\":\"2020-01-01T23:59:59-03:00\",\"duePrecision\":\"date_only\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/home").session(s.session()))
        .andExpect(jsonPath("$.applicationsWithNextActions[0].id").value(id));
    mvc.perform(get("/api/applications/" + id).session(s.session()))
        .andExpect(jsonPath("$.status").value("interview"))
        .andExpect(jsonPath("$.history[1].author").value("user"))
        .andExpect(jsonPath("$.history[0].toStatus").value("submitted_awaiting"));
    mvc.perform(
            post("/api/applications/" + id + "/status")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"invented\",\"reason\":\"teste\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/applications/missing").session(s.session()))
        .andExpect(status().isNotFound());
  }
}
