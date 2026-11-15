package dev.curriculotron.opportunities.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class CanonicalUrl {
  private CanonicalUrl() {}

  public static String normalize(String value) {
    try {
      URI uri = URI.create(value.strip());
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!Set.of("https", "http").contains(scheme)
          || uri.getHost() == null
          || uri.getRawUserInfo() != null
          || uri.getPort() > 65535) {
        throw new IllegalArgumentException();
      }
      int port = uri.getPort();
      boolean defaultPort =
          port == -1
              || (scheme.equals("https") && port == 443)
              || (scheme.equals("http") && port == 80);
      String path = uri.getRawPath();
      String query =
          uri.getRawQuery() == null
              ? ""
              : Arrays.stream(uri.getRawQuery().split("&"))
                  .filter(part -> {
                    String name = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !name.startsWith("utm_") && !Set.of("fbclid", "gclid").contains(name);
                  })
                  .collect(Collectors.joining("&"));
      return scheme
          + "://"
          + uri.getHost().toLowerCase(Locale.ROOT)
          + (defaultPort ? "" : ":" + port)
          + (path == null || path.isEmpty() ? "/" : path)
          + (query.isEmpty() ? "" : "?" + query);
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("Informe uma URL HTTP ou HTTPS válida, sem credenciais.");
    }
  }
}
