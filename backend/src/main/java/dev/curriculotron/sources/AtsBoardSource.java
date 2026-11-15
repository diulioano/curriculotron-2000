package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import java.net.URI;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Official public job boards only. Configuration is provider:account, never an arbitrary URL. */
public class AtsBoardSource implements JobSource {
  private final String provider;
  private final String account;
  private final PublicSourceHttp http;

  public AtsBoardSource(String provider, String account, PublicSourceHttp http) {
    if (!Set.of("greenhouse", "lever", "workable").contains(provider)
        || account == null
        || !account.matches("[A-Za-z0-9_-]{1,100}"))
      throw new IllegalArgumentException("Board inválido");
    this.provider = provider;
    this.account = account;
    this.http = http;
  }

  public URI endpoint() {
    return URI.create(
        switch (provider) {
          case "greenhouse" ->
              "https://boards-api.greenhouse.io/v1/boards/" + account + "/jobs?content=true";
          case "lever" -> "https://api.lever.co/v0/postings/" + account + "?mode=json";
          default -> "https://www.workable.com/api/accounts/" + account + "?details=true";
        });
  }

  public SourceResult fetch() {
    String source = provider + ":" + account;
    try {
      JsonNode root = JsonMapper.builder().build().readTree(http.get(endpoint()));
      JsonNode rows = provider.equals("lever") ? root : root.path("jobs");
      if (!rows.isArray()) throw new IllegalArgumentException("Formato inesperado");
      var items = new ArrayList<Item>();
      for (JsonNode row : rows) {
        String id = row.path(provider.equals("workable") ? "shortcode" : "id").asText("");
        String url =
            row.path(
                    switch (provider) {
                      case "greenhouse" -> "absolute_url";
                      case "lever" -> "hostedUrl";
                      default -> "url";
                    })
                .asText("");
        String title = row.path(provider.equals("lever") ? "text" : "title").asText("");
        if (id.isBlank() || url.isBlank() || title.isBlank())
          throw new IllegalArgumentException("Vaga incompleta");
        String content =
            row.path(
                    provider.equals("greenhouse")
                        ? "content"
                        : provider.equals("lever") ? "descriptionPlain" : "description")
                .asText("");
        items.add(
            new Item(
                source,
                id,
                CanonicalUrl.normalize(url),
                title + "\n" + content,
                title,
                location(row),
                PublicationDates.parse(
                    row.path(provider.equals("lever") ? "createdAt" : "published_at").asText(""))));
      }
      return new SourceResult(
          items,
          List.of(),
          List.of(
              "Cobertura limitada ao board público configurado; salário e modalidade exigem confirmação."));
    } catch (Exception e) {
      return SourceResult.failed(source, e);
    }
  }

  private String location(JsonNode row) {
    var parts = new ArrayList<String>();
    switch (provider) {
      case "greenhouse" -> parts.add(row.path("location").path("name").asText(""));
      case "lever" -> {
        parts.add(row.path("categories").path("location").asText(""));
        parts.add(row.path("workplaceType").asText(""));
      }
      default -> {
        parts.add(row.path("city").asText(""));
        if (row.path("telecommuting").asBoolean(false)) parts.add("remote");
      }
    }
    String joined = String.join(" · ", parts.stream().filter(p -> !p.isBlank()).toList());
    return joined.isBlank() ? null : joined;
  }
}
