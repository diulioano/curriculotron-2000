package dev.curriculotron.idempotency.adapter.in.web;

import dev.curriculotron.idempotency.application.IdempotencyService;
import dev.curriculotron.idempotency.domain.StoredResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
  private static final int MAX_REQUEST_BODY_BYTES = 5 * 1024 * 1024;
  // The app runs as one process per database; bounded stripes serialize duplicate keys in that JVM.
  private static final Object[] KEY_LOCKS = locks();
  private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final IdempotencyService idempotency;

  public IdempotencyFilter(IdempotencyService idempotency) {
    this.idempotency = idempotency;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String key = request.getHeader(HEADER);
    return "/api/profile/import/preview".equals(request.getRequestURI())
        || key == null
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
    if (request.getContentLengthLong() > MAX_REQUEST_BODY_BYTES) {
      writeError(response, 413, "request_too_large", "O corpo da requisição excede o limite.");
      return;
    }
    byte[] requestBody = request.getInputStream().readNBytes(MAX_REQUEST_BODY_BYTES + 1);
    if (requestBody.length > MAX_REQUEST_BODY_BYTES) {
      writeError(response, 413, "request_too_large", "O corpo da requisição excede o limite.");
      return;
    }
    String requestFingerprint = fingerprint(requestBody);
    var replayableRequest = new CachedBodyRequest(request, requestBody);

    Object keyLock = KEY_LOCKS[Math.floorMod(key.hashCode(), KEY_LOCKS.length)];
    synchronized (keyLock) {
      Optional<StoredResponse> stored = idempotency.find(key);
      if (stored.isPresent()) {
        replay(stored.get(), method, path, requestFingerprint, response);
        return;
      }

      var wrapper = new ContentCachingResponseWrapper(response);
      chain.doFilter(replayableRequest, wrapper);
      int status = wrapper.getStatus();
      if (status >= 200 && status < 300) {
        idempotency.remember(
            key,
            method,
            path,
            status,
            wrapper.getContentType(),
            wrapper.getContentAsByteArray(),
            requestFingerprint);
      }
      wrapper.copyBodyToResponse();
    }
  }

  private static String fingerprint(byte[] body) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 indisponível.", impossible);
    }
  }

  private static Object[] locks() {
    Object[] locks = new Object[64];
    for (int index = 0; index < locks.length; index++) locks[index] = new Object();
    return locks;
  }

  private static void replay(
      StoredResponse s,
      String method,
      String path,
      String requestFingerprint,
      HttpServletResponse response)
      throws IOException {
    if (!s.matches(method, path, requestFingerprint)) {
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

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      var input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          try {
            if (isFinished()) listener.onAllDataRead();
            else listener.onDataAvailable();
          } catch (IOException error) {
            listener.onError(error);
          }
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      return new BufferedReader(
          new InputStreamReader(
              getInputStream(),
              encoding == null
                  ? StandardCharsets.UTF_8
                  : java.nio.charset.Charset.forName(encoding)));
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
