package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.CanonicalUrl;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Public opportunities page of CWI ({@code cwi.com.br/talentos/oportunidades/}). Reads only the
 * cards listed on the page (title and pills); restricted source because the markup is not an API.
 */
public class CwiCareersSource implements JobSource {
  static final URI ENDPOINT = URI.create("https://cwi.com.br/talentos/oportunidades/");

  private static final Pattern CARD =
      Pattern.compile(
          "<a href=\"(https://cwi\\.com\\.br/talentos/oportunidade/[a-z0-9-]+-(\\d+))\"[^>]*>(.*?)</a>",
          Pattern.DOTALL);
  private static final Pattern TITLE =
      Pattern.compile("class=\"opportunity-card__text\">(.*?)</span>", Pattern.DOTALL);
  private static final Pattern PILL =
      Pattern.compile("class=\"pills__pill\">(.*?)</span>", Pattern.DOTALL);

  private final PublicSourceHttp http;

  public CwiCareersSource(PublicSourceHttp http) {
    this.http = http;
  }

  @Override
  public SourceResult fetch() {
    String source = "cwi:oportunidades";
    try {
      var matcher = CARD.matcher(http.get(ENDPOINT));
      var byId = new LinkedHashMap<String, Item>();
      while (matcher.find()) {
        var title = TITLE.matcher(matcher.group(3));
        if (!title.find()) continue;
        String text = clean(title.group(1));
        if (text.isBlank()) continue;
        var pills = new ArrayList<String>();
        var pill = PILL.matcher(matcher.group(3));
        while (pill.find()) pills.add(clean(pill.group(1)));
        String location = pills.isEmpty() ? null : String.join(" · ", pills);
        byId.putIfAbsent(
            matcher.group(2),
            new Item(
                source,
                matcher.group(2),
                CanonicalUrl.normalize(matcher.group(1)),
                location == null ? text : text + "\n" + location,
                text,
                location));
      }
      if (byId.isEmpty()) throw new IllegalArgumentException("Nenhuma vaga reconhecida");
      return new SourceResult(
          List.copyOf(byId.values()),
          List.of(),
          List.of(
              "Fonte restrita: cartões da página pública da CWI; requisitos, salário e contrato exigem confirmação no link."));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return SourceResult.failed(source, e);
    }
  }

  private static String clean(String html) {
    return html.replaceAll("<[^>]+>", " ")
        .replace("&amp;", "&")
        .replace("&#8211;", "–")
        .replace("&nbsp;", " ")
        .replaceAll("\\s+", " ")
        .strip();
  }
}
