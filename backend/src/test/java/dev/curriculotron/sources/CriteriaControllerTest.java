package dev.curriculotron.sources;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.curriculotron.support.AppTestBase;
import dev.curriculotron.support.TestSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class CriteriaControllerTest extends AppTestBase {
  @Autowired MockMvc mvc;

  private static final String REQUEST =
      """
      {"version":1,"targetRoles":["Backend Developer"],"stackKeywords":[" Python ","python",""],
       "remotePreferred":true,"hybridRegions":[],"cltMinMonthlyBrl":6000,"pjMinMonthlyBrl":9000,
       "acceptJuniorIfJustified":true,"dailySearchTimeLocal":"07:30","deadlineHighlightDays":7,
       "urgentHours":48,"emailDigestEnabled":false}
      """;

  @Test
  void shouldSaveVersionedCriteriaWithSessionAndCsrfAndRejectInvalidBodies() throws Exception {
    mvc.perform(get("/api/criteria")).andExpect(status().isUnauthorized());
    TestSession.setup(mvc, TestSession.PASSWORD);
    var session = TestSession.login(mvc, TestSession.PASSWORD);
    mvc.perform(get("/api/criteria").session(session.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.assessment.state").value("pending"));
    mvc.perform(
            put("/api/criteria")
                .session(session.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isForbidden());
    for (String invalid :
        new String[] {
          REQUEST.replace("6000", "0"),
          REQUEST.replace("6000", "-1"),
          REQUEST.replace("07:30", "29:00"),
          REQUEST.replace("\"urgentHours\":48", "\"urgentHours\":0"),
          REQUEST.replace("\"version\":1", "\"version\":0"),
          REQUEST.replace("Backend Developer", "x".repeat(201)),
          REQUEST.replace("\"remotePreferred\":true,", ""),
          "{broken}"
        }) {
      mvc.perform(
              put("/api/criteria")
                  .session(session.session())
                  .header("X-CSRF-Token", session.csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("invalid_criteria"));
    }
    mvc.perform(
            put("/api/criteria")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.stackKeywords[0]").value("Python"))
        .andExpect(jsonPath("$.stackKeywords.length()").value(1))
        .andExpect(jsonPath("$.assessment.stale").value(true));
    mvc.perform(
            put("/api/criteria")
                .session(session.session())
                .header("X-CSRF-Token", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("criteria_version_conflict"));
    mvc.perform(get("/api/criteria").session(session.session()))
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.stackKeywords[0]").value("Python"));
  }
}
