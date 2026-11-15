package dev.curriculotron.sources;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Bounded public GETs, no cookies, credentials or redirect following. */
public class PublicSourceHttp {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public String get(URI uri) throws Exception {
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        var request =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Curriculotron/1.0 (personal job search)")
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
          if (response.statusCode() == 200) {
            byte[] bytes = body.readNBytes(5_000_001);
            if (bytes.length > 5_000_000)
              throw new IllegalArgumentException("Fonte excedeu limite de tamanho.");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
          }
          if (response.statusCode() < 500 && response.statusCode() != 429)
            throw new IllegalArgumentException("Fonte recusou acesso público.");
        }
      } catch (java.io.IOException e) {
        if (attempt == 2) throw e;
      }
      if (attempt < 2) Thread.sleep(500L << attempt);
    }
    throw new java.io.IOException("Fonte indisponível após três tentativas.");
  }
}
