package dev.curriculotron.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.curriculotron.support.AppTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("local")
class LocalModeSecurityTest extends AppTestBase {
  @Autowired MockMvc mvc;

  @Test
  void localSessionNeedsNoPasswordButMutationsStillNeedCsrf() throws Exception {
    mvc.perform(get("/api/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.setupRequired").value(false))
        .andExpect(jsonPath("$.authMode").value("local"))
        .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    mvc.perform(get("/api/home")).andExpect(status().isOk());
    mvc.perform(post("/api/opportunities/manual").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }
}
