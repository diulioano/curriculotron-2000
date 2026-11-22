package dev.curriculotron.opportunities.domain;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Preparação para a vaga (US006): tecnologias citadas, agrupadas por obrigatória, diferencial ou
 * menção, e comparadas com o perfil atual. Calculada na leitura; é recomendação de estudo, nunca
 * evidência de competência.
 */
public final class StudyPreparation {

  public enum Requirement {
    required,
    optional,
    mentioned
  }

  public enum Tier {
    primary,
    secondary,
    flexible,
    none
  }

  /** Tecnologias do perfil por peso; vazio quando não há perfil salvo. */
  public record ProfileTechnologies(
      Long version, String primary, List<String> secondary, List<String> flexible) {
    public static final ProfileTechnologies NONE =
        new ProfileTechnologies(null, null, List.of(), List.of());

    public ProfileTechnologies {
      secondary = List.copyOf(secondary);
      flexible = List.copyOf(flexible);
    }
  }

  public record Item(String technology, Tier profileTier, String quote) {}

  public record Plan(
      Long profileVersion, List<Item> required, List<Item> optional, List<Item> mentioned) {
    public Plan {
      required = List.copyOf(required);
      optional = List.copyOf(optional);
      mentioned = List.copyOf(mentioned);
    }
  }

  /** Tecnologias comuns em vagas de desenvolvimento, além das do perfil. */
  static final List<String> CATALOG =
      List.of(
          "Java",
          "Spring Boot",
          "Kotlin",
          "Python",
          "Golang",
          "Node.js",
          "TypeScript",
          "JavaScript",
          "React",
          "Angular",
          "Vue.js",
          "C#",
          ".NET",
          "PHP",
          "Ruby",
          "Scala",
          "Docker",
          "Kubernetes",
          "AWS",
          "Azure",
          "GCP",
          "Terraform",
          "Kafka",
          "RabbitMQ",
          "PostgreSQL",
          "MySQL",
          "Oracle",
          "SQL Server",
          "MongoDB",
          "Redis",
          "Elasticsearch",
          "GraphQL",
          "Microsserviços",
          "Microservices",
          "JUnit",
          "Mockito",
          "Hibernate",
          "Quarkus",
          "Jenkins",
          "Git",
          "Linux");

  private final TechnologyMatcher matcher = new TechnologyMatcher();

  public Plan plan(List<VacancyEvidence> evidence, ProfileTechnologies profile) {
    var tiers = tiers(profile);
    var terms = new LinkedHashMap<String, String>();
    for (String term : tiers.keySet()) terms.put(term, display(profile, term));
    for (String term : CATALOG) terms.putIfAbsent(base(term), term);

    var best = new LinkedHashMap<String, Requirement>();
    var quotes = new LinkedHashMap<String, String>();
    var negated = new HashSet<String>();
    for (var item : matcher.match(evidence, List.copyOf(terms.values())).evidence()) {
      String key = base(item.matchedCriterion());
      if (item.mentionKind() == MentionKind.negated) negated.add(key);
      var requirement = requirement(item.mentionKind());
      if (requirement == null) continue;
      var previous = best.get(key);
      if (previous == null || requirement.ordinal() < previous.ordinal()) {
        best.put(key, requirement);
        quotes.put(key, item.quote());
      }
    }
    var groups = new LinkedHashMap<Requirement, List<Item>>();
    for (var requirement : Requirement.values()) groups.put(requirement, new ArrayList<>());
    best.forEach((key, requirement) -> {
      if (negated.contains(key)) return;
      groups
          .get(requirement)
          .add(new Item(terms.get(key), tiers.getOrDefault(key, Tier.none), quotes.get(key)));
    });
    return new Plan(
        profile.version(),
        groups.get(Requirement.required),
        groups.get(Requirement.optional),
        groups.get(Requirement.mentioned));
  }

  private static Requirement requirement(MentionKind kind) {
    return switch (kind) {
      case required -> Requirement.required;
      case optional -> Requirement.optional;
      case usage, uncertain -> Requirement.mentioned;
      case negated, incidental -> null;
    };
  }

  private static Map<String, Tier> tiers(ProfileTechnologies profile) {
    var tiers = new LinkedHashMap<String, Tier>();
    if (profile.primary() != null) tiers.put(base(profile.primary()), Tier.primary);
    for (String value : profile.secondary()) tiers.putIfAbsent(base(value), Tier.secondary);
    for (String value : profile.flexible()) tiers.putIfAbsent(base(value), Tier.flexible);
    return tiers;
  }

  private static String display(ProfileTechnologies profile, String key) {
    var all = new ArrayList<String>();
    if (profile.primary() != null) all.add(profile.primary());
    all.addAll(profile.secondary());
    all.addAll(profile.flexible());
    return all.stream().filter(value -> base(value).equals(key)).findFirst().orElse(key);
  }

  /** Nome sem acento, caixa ou versão final: "Java 17" e "java" são a mesma tecnologia. */
  static String base(String value) {
    return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceFirst("\\s+\\d+(?:\\.\\d+)*$", "")
        .replaceAll("\\s+", " ");
  }
}
