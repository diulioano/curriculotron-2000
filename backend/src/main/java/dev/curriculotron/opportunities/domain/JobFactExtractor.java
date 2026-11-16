package dev.curriculotron.opportunities.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts only literal facts from a posting (title, location, listed text) for rule-based ranking.
 * Nothing is inferred: absent information stays {@code unknown}.
 */
public final class JobFactExtractor {
  public record Facts(
      String title,
      String company,
      String location,
      String workMode,
      String region,
      String seniority,
      boolean roleCompatible,
      Set<String> stackFacts) {
    public Facts {
      stackFacts = Set.copyOf(stackFacts);
    }
  }

  private static final Pattern DEV_ROLE =
      Pattern.compile(
          "\\b(desenvolvedor(a)?|developer|programador(a)?|engenheir[oa](\\(a\\))? de software|software engineer|dev)\\b");
  private static final Pattern ROLE_FOCUS =
      Pattern.compile(
          "\\b(java|back ?-?end|full ?-?stack|software engineer|engenheir[oa](\\(a\\))? de software)\\b");
  private static final Map<String, List<String>> REGION_CITIES =
      Map.of(
          "rm porto alegre",
          List.of(
              "porto alegre",
              "canoas",
              "sao leopoldo",
              "novo hamburgo",
              "gravatai",
              "cachoeirinha",
              "esteio",
              "sapucaia do sul",
              "guaiba",
              "alvorada",
              "viamao",
              "eldorado do sul"));

  public Facts extract(
      String title,
      String company,
      String location,
      String text,
      Set<String> stackKeywords,
      Set<String> hybridRegions) {
    String safeTitle = title == null || title.isBlank() ? firstLine(text) : title.strip();
    String nTitle = normalize(safeTitle);
    String nLocation = normalize(location);
    String nText = normalize(text);

    var stack = new LinkedHashSet<String>();
    var ordered = stackKeywords.stream().map(String::strip).filter(k -> !k.isBlank()).toList();
    if (!ordered.isEmpty()) {
      var probe =
          new VacancyEvidence(
              "extract",
              "extract",
              null,
              "extract",
              title,
              location,
              text,
              VacancyEvidence.Completeness.unknown,
              null,
              1,
              null,
              null);
      var evidence = new TechnologyMatcher().match(List.of(probe), ordered).evidence();
      for (String keyword : ordered) {
        boolean hasNegative =
            evidence.stream()
                .anyMatch(ev ->
                    keyword.equals(ev.matchedCriterion())
                        && ev.mentionKind() == CompatibilityAssessment.MentionKind.negated);
        boolean hasLiteralMention =
            evidence.stream()
                .anyMatch(ev ->
                    keyword.equals(ev.matchedCriterion())
                        && ev.mentionKind() != CompatibilityAssessment.MentionKind.negated
                        && ev.mentionKind() != CompatibilityAssessment.MentionKind.incidental
                        && (ev.configuredVersion() == null
                            || ev.configuredVersion().equals(ev.observedVersion())));
        if (!hasNegative && hasLiteralMention) stack.add(keyword);
      }
    }

    boolean role = DEV_ROLE.matcher(nTitle).find() && ROLE_FOCUS.matcher(nTitle).find();

    String workMode = workMode(nLocation);
    if (workMode.equals("unknown")) workMode = workMode(nTitle);
    if (workMode.equals("unknown")) workMode = workMode(nText);

    return new Facts(
        safeTitle.length() > 200 ? safeTitle.substring(0, 200) : safeTitle,
        company,
        location,
        workMode,
        region(nLocation + " " + nTitle, hybridRegions, location),
        seniority(nTitle),
        role,
        stack);
  }

  /** Literal requirement facts kept on the opportunity (source of {@code requirements_facts}). */
  public static List<String> factList(Facts facts) {
    var list = new ArrayList<String>();
    for (String s : facts.stackFacts()) list.add("Menciona " + s);
    if (!facts.workMode().equals("unknown")) list.add("Modalidade informada: " + facts.workMode());
    if (facts.location() != null) list.add("Local informado: " + facts.location());
    return list;
  }

  private static String workMode(String n) {
    if (n.isBlank()) return "unknown";
    if (n.matches("(?s).*\\b(remoto|remota|remote|home office|anywhere)\\b.*")) return "remote";
    if (n.matches("(?s).*\\b(hibrido|hibrida|hybrid)\\b.*")) return "hybrid";
    if (n.matches("(?s).*\\b(presencial|on-site|onsite|on site)\\b.*")) return "onsite";
    return "unknown";
  }

  private static String seniority(String n) {
    if (n.matches("(?s).*\\b(junior|jr)\\b.*")) return "junior";
    if (n.matches("(?s).*\\b(senior|sr)\\b.*")) return "senior";
    if (n.matches("(?s).*\\b(pleno|pl|mid)\\b.*")) return "pleno";
    return null;
  }

  private static String region(String n, Set<String> regions, String location) {
    for (String region : regions) {
      String r = normalize(region);
      if (n.contains(r)) return region;
      for (String city : REGION_CITIES.getOrDefault(r, List.of()))
        if (n.contains(city)) return region;
    }
    return location;
  }

  private static String firstLine(String text) {
    if (text == null || text.isBlank()) return "Vaga a revisar";
    return text.strip().lines().findFirst().orElse("Vaga a revisar");
  }

  static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
