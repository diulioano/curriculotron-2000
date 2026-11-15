package dev.curriculotron.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Sessão de navegador simulada: cookie de sessão + token CSRF. */
public record TestSession(MockHttpSession session, String csrf) {

  public static final String PASSWORD = "senha-de-teste-123";

  /** Visita anônima: GET /api/session cria a sessão e devolve o token CSRF. */
  public static TestSession anonymous(MockMvc mvc) throws Exception {
    MvcResult r = mvc.perform(get("/api/session")).andReturn();
    return new TestSession((MockHttpSession) r.getRequest().getSession(false), csrfFrom(r));
  }

  public static void setup(MockMvc mvc, String password) throws Exception {
    TestSession s = anonymous(mvc);
    mvc.perform(
            post("/api/setup")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(password)))
        .andReturn();
  }

  public static MvcResult attemptLogin(MockMvc mvc, String password) throws Exception {
    TestSession s = anonymous(mvc);
    return mvc.perform(
            post("/api/session")
                .session(s.session())
                .header("X-CSRF-Token", s.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(password)))
        .andReturn();
  }

  /** Faz login (exige setup feito) e devolve a nova sessão autenticada. */
  public static TestSession login(MockMvc mvc, String password) throws Exception {
    MvcResult r = attemptLogin(mvc, password);
    if (r.getResponse().getStatus() != 200) {
      throw new AssertionError("login falhou: " + r.getResponse().getStatus());
    }
    return new TestSession((MockHttpSession) r.getRequest().getSession(false), csrfFrom(r));
  }

  public static String json(String password) {
    return "{\"password\":\"" + password + "\"}";
  }

  private static String csrfFrom(MvcResult r) throws Exception {
    return JsonPath.read(r.getResponse().getContentAsString(), "$.csrfToken");
  }
}
