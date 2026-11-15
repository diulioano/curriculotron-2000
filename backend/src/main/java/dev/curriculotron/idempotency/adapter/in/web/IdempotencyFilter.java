package dev.curriculotron.idempotency.adapter.in.web;

import dev.curriculotron.idempotency.application.IdempotencyService;
import dev.curriculotron.idempotency.domain.StoredResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * {@code Idempotency-Key} opcional em mutações (contracts/panel-api.md): repetição com a mesma
 * chave devolve o mesmo status e corpo. Só respostas 2xx são memorizadas; uma chave usada em outra
 * rota é rejeitada com 422.
 *
 * <p>Registrado na cadeia do Spring Security depois da autenticação (ver SecurityConfig), para que
 * só requisições autenticadas e com CSRF válido cheguem aqui.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

  public static final String HEADER = "Idempotency-Key";
  private static final int MAX_KEY_LENGTH = 200;
  private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final IdempotencyService idempotency;

  public IdempotencyFilter(IdempotencyService idempotency) {
    this.idempotency = idempotency;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String key = request.getHeader(HEADER);
    return key == null
        || key.isBlank()
        || !MUTATIONS.contains(request.getMethod())
        || !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getHeader(HEADER).strip();
    if (key.length() > MAX_KEY_LENGTH) {
      writeError(response, 400, "invalid_idempotency_key", "Idempotency-Key muito longa.");
      return;
    }
    String method = request.getMethod();
    String path = request.getRequestURI();

    Optional<StoredResponse> stored = idempotency.find(key);
    if (stored.isPresent()) {
      replay(stored.get(), method, path, response);
      return;
    }

    var wrapper = new ContentCachingResponseWrapper(response);
    chain.doFilter(request, wrapper);
    int status = wrapper.getStatus();
    if (status >= 200 && status < 300) {
      idempotency.remember(
          key, method, path, status, wrapper.getContentType(), wrapper.getContentAsByteArray());
    }
    wrapper.copyBodyToResponse();
  }

  private static void replay(
      StoredResponse s, String method, String path, HttpServletResponse response)
      throws IOException {
    if (!s.matches(method, path)) {
      writeError(
          response, 422, "idempotency_key_reused", "Idempotency-Key já usada em outra operação.");
      return;
    }
    response.setStatus(s.status());
    response.setHeader("Idempotent-Replayed", "true");
    if (s.contentType() != null) {
      response.setContentType(s.contentType());
    }
    if (s.body() != null) {
      response.getOutputStream().write(s.body());
    }
  }

  private static void writeError(HttpServletResponse response, int status, String code, String msg)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}}");
  }
}
