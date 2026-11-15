package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Public Gupy careers page of one company ({@code <subdomain>.gupy.io}). Reads the job list
 * embedded in the page; registered as a restricted source (D-08) because the format is not a
 * documented API.
 */
public class GupySource implements JobSource {
  private static final Pattern NEXT_DATA =
      Pattern.compile(
          "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);

  private final String subdomain;
  private final PublicSourceHttp http;

  public GupySource(String subdomain, PublicSourceHttp http) {
    if (subdomain == null || !subdomain.matches("[a-z0-9][a-z0-9-]{0,62}"))
      throw new IllegalArgumentException("Página Gupy inválida");
    this.subdomain = subdomain;
    this.http = http;
  }

  public URI endpoint() {
    return URI.create("https://" + subdomain + ".gupy.io/");
  }

  @Override
  public SourceResult fetch() {
    String source = "gupy:" + subdomain;
    try {
      var matcher = NEXT_DATA.matcher(http.get(endpoint()));
      if (!matcher.find()) throw new IllegalArgumentException("Formato inesperado");
      JsonNode rows = findJobs(JsonMapper.builder().build().readTree(matcher.group(1)));
      if (rows == null) throw new IllegalArgumentException("Formato inesperado");
      var items = new ArrayList<Item>();
      for (JsonNode row : rows) {
        String id = row.path("id").asText("");
        String title = row.path("title").asText("");
        if (id.isBlank() || title.isBlank()) throw new IllegalArgumentException("Vaga incompleta");
        JsonNode workplace = row.path("workplace");
        JsonNode address = workplace.path("address");
        var location = new ArrayList<String>();
        String city = address.path("city").asText("");
        String state = address.path("stateShortName").asText("");
        if (!city.isBlank()) location.add(state.isBlank() ? city : city + " - " + state);
        String type = workplace.path("workplaceType").asText("");
        if (!type.isBlank()) location.add(type);
        items.add(
            new Item(
                source,
                id,
                CanonicalUrl.normalize("https://" + subdomain + ".gupy.io/jobs/" + id),
                title + "\n" + row.path("department").asText(""),
                title,
                location.isEmpty() ? null : String.join(" · ", location),
                PublicationDates.parse(row.path("publishedDate").asText(""))));
      }
      return new SourceResult(
          items,
          List.of(),
          List.of(
              "Fonte restrita: lista pública da página Gupy, sem descrição completa; requisitos, salário e contrato exigem confirmação no link."));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return SourceResult.failed(source, e);
    }
  }

  private static JsonNode findJobs(JsonNode node) {
    if (node == null) return null;
    if (node.isObject()) {
      JsonNode jobs = node.get("jobs");
      if (jobs != null && jobs.isArray()) return jobs;
      for (JsonNode child : node.values()) {
        JsonNode found = findJobs(child);
        if (found != null) return found;
      }
    }
    return null;
  }
}
