package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Public Oracle Recruiting Cloud (Candidate Experience) listing. Account is {@code
 * <pod>.fa.<datacenter>/<siteNumber>}, e.g. {@code epdm.fa.la1/CX_3001}; the host is always {@code
 * *.oraclecloud.com}.
 */
public class OracleRecruitingSource implements JobSource {
  private final String host;
  private final String site;
  private final PublicSourceHttp http;

  public OracleRecruitingSource(String account, PublicSourceHttp http) {
    if (account == null
        || !account.matches("[a-z0-9]{2,20}\\.fa\\.[a-z0-9]{2,10}/[A-Za-z0-9_]{1,40}"))
      throw new IllegalArgumentException("Site Oracle inválido");
    String[] parts = account.split("/", 2);
    this.host = parts[0] + ".oraclecloud.com";
    this.site = parts[1];
    this.http = http;
  }

  public URI endpoint() {
    return URI.create(
        "https://"
            + host
            + "/hcmRestApi/resources/latest/recruitingCEJobRequisitions?onlyData=true"
            + "&expand=requisitionList&finder=findReqs;siteNumber="
            + site
            + ",limit=200,sortBy=POSTING_DATES_DESC");
  }

  @Override
  public SourceResult fetch() {
    String source = "oracle:" + host.replace(".oraclecloud.com", "") + "/" + site;
    try {
      JsonNode rows =
          JsonMapper.builder()
              .build()
              .readTree(http.get(endpoint()))
              .path("items")
              .path(0)
              .path("requisitionList");
      if (!rows.isArray()) throw new IllegalArgumentException("Formato inesperado");
      var items = new ArrayList<Item>();
      for (JsonNode row : rows) {
        String id = row.path("Id").asText("");
        String title = row.path("Title").asText("");
        if (id.isBlank() || title.isBlank()) throw new IllegalArgumentException("Vaga incompleta");
        String url =
            "https://" + host + "/hcmUI/CandidateExperience/pt-BR/sites/" + site + "/job/" + id;
        String description = row.path("ShortDescriptionStr").asText("");
        var location = new ArrayList<String>();
        for (String field : List.of("PrimaryLocation", "WorkplaceType")) {
          String value = row.path(field).asText("");
          if (!value.isBlank() && !"null".equals(value)) location.add(value);
        }
        items.add(
            new Item(
                source,
                id,
                CanonicalUrl.normalize(url),
                title + "\n" + description,
                title,
                location.isEmpty() ? null : String.join(" · ", location),
                PublicationDates.parse(row.path("PostedDate").asText(""))));
      }
      return new SourceResult(
          items,
          List.of(),
          List.of(
              "Listagem pública do portal de carreiras; descrição completa, salário e modalidade exigem confirmação no link."));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return SourceResult.failed(source, e);
    }
  }
}
