package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        Description description = description(row);
        items.add(
            new Item(
                source,
                id,
                CanonicalUrl.normalize(url),
                title + "\n" + description.text(),
                title,
                location(row),
                PublicationDates.parse(
                    row.path(provider.equals("lever") ? "createdAt" : "published_at").asText("")),
                description.completeness(),
                description.reason()));
      }
      return new SourceResult(
          items,
          List.of(),
          List.of(
              "Cobertura limitada ao board público configurado; salário e modalidade exigem confirmação."));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return SourceResult.failed(source, e);
    }
  }

  private record Description(String text, Completeness completeness, String reason) {}

  private Description description(JsonNode row) {
    if (provider.equals("lever")) return leverDescription(row);
    String content = textValue(row.path(provider.equals("greenhouse") ? "content" : "description"));
    String readable = readableHtml(content);
    boolean complete = provider.equals("greenhouse") && !readable.isBlank();
    return new Description(
        readable,
        complete ? Completeness.complete : Completeness.unknown,
        complete ? null : readable.isBlank() ? "missing_description" : "unverified_description");
  }

  private Description leverDescription(JsonNode row) {
    var parts = new LinkedHashSet<String>();
    boolean unexpected = false;
    boolean hasDescription = false;
    for (String field :
        List.of("openingPlain", "descriptionPlain", "descriptionBodyPlain", "additionalPlain")) {
      JsonNode value = row.get(field);
      if (value != null && !value.isString()) {
        unexpected = true;
      }
      String text = value == null ? "" : textValue(value);
      if (text.isBlank()) {
        String htmlField = field.substring(0, field.length() - "Plain".length());
        JsonNode html = row.get(htmlField);
        if (html != null && !html.isString()) unexpected = true;
        if (html != null && html.isString()) text = readableHtml(html.asString(""));
      }
      if (!text.isBlank()) {
        parts.add(text);
        if (field.equals("descriptionPlain") || field.equals("descriptionBodyPlain"))
          hasDescription = true;
      }
    }
    JsonNode lists = row.get("lists");
    if (lists != null) {
      if (!lists.isArray()) {
        unexpected = true;
      } else {
        for (JsonNode list : lists) {
          String heading = textValue(list.path("text"));
          String content = readableHtml(textValue(list.path("content")));
          if (!list.isObject() || content.isBlank()) unexpected = true;
          if (!heading.isBlank()) parts.add(heading);
          if (!content.isBlank()) parts.add(content);
        }
      }
    }
    boolean complete = hasDescription && !unexpected;
    return new Description(
        String.join("\n", parts),
        complete ? Completeness.complete : Completeness.unknown,
        complete ? null : unexpected ? "unexpected_description_format" : "missing_description");
  }

  private static String textValue(JsonNode value) {
    return value.isString() ? value.asString("") : "";
  }

  private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

  private static String readableHtml(String html) {
    String text =
        html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", " ")
            .replaceAll("(?i)</?(?:p|div|h[1-6]|li|ul|ol|br)\\b[^>]*>", "\n")
            .replaceAll("<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    text =
        NUMERIC_ENTITY
            .matcher(text)
            .replaceAll(match -> {
              String value = match.group(1);
              try {
                int codePoint =
                    Integer.parseInt(
                        value.startsWith("x") ? value.substring(1) : value,
                        value.startsWith("x") ? 16 : 10);
                return Matcher.quoteReplacement(
                    Character.isValidCodePoint(codePoint)
                        ? new String(Character.toChars(codePoint))
                        : match.group());
              } catch (NumberFormatException exception) {
                return Matcher.quoteReplacement(match.group());
              }
            });
    return text.replaceAll("[\\t ]+", " ").replaceAll(" *\\n[\\s\\n]*", "\n").strip();
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
