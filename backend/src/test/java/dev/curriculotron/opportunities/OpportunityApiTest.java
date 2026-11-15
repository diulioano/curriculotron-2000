package dev.curriculotron.opportunities;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import dev.curriculotron.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class OpportunityApiTest extends AppTestBase {
  @Autowired MockMvc mvc;

  @Test
  void manualReviewDiscardAndRestore() throws Exception {
    TestSession.setup(mvc, TestSession.PASSWORD);
    var s = TestSession.login(mvc, TestSession.PASSWORD);
    var response =
        mvc.perform(
                post("/api/opportunities/manual")
                    .session(s.session())
                    .header("X-CSRF-Token", s.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/jobs/123\",\"text\":\"Java remoto\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String id = JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    mvc.perform(get("/api/opportunities/" + id).session(s.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").isEmpty())
        .andExpect(jsonPath("$.facts[0]").value("Java remoto"))
        .andExpect(jsonPath("$.inferred").isEmpty())
        .andExpect(jsonPath("$.sources[0].url").value("https://example.com/jobs/123"))
        .andExpect(jsonPath("$.sources[0].fetchedAt").exists())
        .andExpect(
            jsonPath("$.missing")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "Salário a confirmar",
                        "Tipo de contrato a confirmar",
                        "Localização a confirmar")));
    mvc.perform(get("/api/opportunities?list=unknown").session(s.session()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_list"));
    mvc.perform(get("/api/opportunities?date=ontem").session(s.session()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_parameter"));
    mvc.perform(get("/api/opportunities/nao-existe").session(s.session()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/opportunities/manual")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"ftp://example.com/x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_input"));
    mvc.perform(
            post("/api/opportunities/" + id + "/discard")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/opportunities?list=discarded").session(s.session()))
        .andExpect(jsonPath("$.items[0].id").value(id));
    mvc.perform(
            post("/api/opportunities/" + id + "/restore")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/opportunities?list=secondary").session(s.session()))
        .andExpect(jsonPath("$.items[0].id").value(id));
  }
}
