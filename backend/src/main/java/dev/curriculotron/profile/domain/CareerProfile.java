package dev.curriculotron.profile.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Perfil de carreira (US006) usado no ranking e na preparação. Importação Markdown revisada e
 * edição manual chegam aqui pelo mesmo caminho, então as invariantes valem para ambas.
 */
public record CareerProfile(
    long version,
    String role,
    String primaryTechnology,
    List<String> secondaryTechnologies,
    List<String> flexibleTechnologies,
    Seniority currentSeniority,
    List<Seniority> targetLevels,
    List<String> priorities,
    BigDecimal juniorFloorCltMonthlyBrl,
    BigDecimal juniorFloorPjMonthlyBrl,
    Origin origin,
    Map<ProfileDraft.Field, FieldProvenance> provenance) {

  public enum Origin {
    markdown,
    manual
  }

  static final int MAX_TECHNOLOGIES = 30;
  static final int MAX_PRIORITIES = 20;

  public CareerProfile {
    if (version < 1) throw new InvalidProfileException("version");
    role = text(role, 200, "role");
    primaryTechnology = text(primaryTechnology, 100, "primaryTechnology");
    var taken = new HashSet<String>(Set.of(key(primaryTechnology)));
    secondaryTechnologies =
        distinct(secondaryTechnologies, MAX_TECHNOLOGIES, 100, taken, "secondaryTechnologies");
    flexibleTechnologies =
        distinct(flexibleTechnologies, MAX_TECHNOLOGIES, 100, taken, "flexibleTechnologies");
    if (currentSeniority == null) throw new InvalidProfileException("currentSeniority");
    if (targetLevels == null
        || targetLevels.isEmpty()
        || targetLevels.stream().anyMatch(Objects::isNull))
      throw new InvalidProfileException("targetLevels");
    var levels = EnumSet.copyOf(targetLevels);
    if (levels.size() != targetLevels.size()) throw new InvalidProfileException("targetLevels");
    targetLevels = List.copyOf(levels);
    priorities = distinct(priorities, MAX_PRIORITIES, 200, new HashSet<>(), "priorities");
    juniorFloorCltMonthlyBrl = money(juniorFloorCltMonthlyBrl, "juniorFloorCltMonthlyBrl");
    juniorFloorPjMonthlyBrl = money(juniorFloorPjMonthlyBrl, "juniorFloorPjMonthlyBrl");
    if (origin == null) throw new InvalidProfileException("origin");
    provenance = FieldProvenance.copy(provenance);
  }

  /** Perfil sem origem por campo registrada (clientes que não enviam metadados). */
  public CareerProfile(
      long version,
      String role,
      String primaryTechnology,
      List<String> secondaryTechnologies,
      List<String> flexibleTechnologies,
      Seniority currentSeniority,
      List<Seniority> targetLevels,
      List<String> priorities,
      BigDecimal juniorFloorCltMonthlyBrl,
      BigDecimal juniorFloorPjMonthlyBrl,
      Origin origin) {
    this(
        version,
        role,
        primaryTechnology,
        secondaryTechnologies,
        flexibleTechnologies,
        currentSeniority,
        targetLevels,
        priorities,
        juniorFloorCltMonthlyBrl,
        juniorFloorPjMonthlyBrl,
        origin,
        Map.of());
  }

  /** Tecnologias na ordem de peso: principal, secundárias e flexíveis. */
  public List<String> technologies() {
    var all = new ArrayList<String>();
    all.add(primaryTechnology);
    all.addAll(secondaryTechnologies);
    all.addAll(flexibleTechnologies);
    return List.copyOf(all);
  }

  public CareerProfile withVersion(long value) {
    return new CareerProfile(
        value,
        role,
        primaryTechnology,
        secondaryTechnologies,
        flexibleTechnologies,
        currentSeniority,
        targetLevels,
        priorities,
        juniorFloorCltMonthlyBrl,
        juniorFloorPjMonthlyBrl,
        origin,
        provenance);
  }

  /** Mesmo conteúdo de ranking, ignorando versão, origem e proveniência por campo. */
  public boolean sameContent(CareerProfile other) {
    return other != null && rankingContent().equals(other.rankingContent());
  }

  private CareerProfile rankingContent() {
    return new CareerProfile(
        1,
        role,
        primaryTechnology,
        secondaryTechnologies,
        flexibleTechnologies,
        currentSeniority,
        targetLevels,
        priorities,
        juniorFloorCltMonthlyBrl,
        juniorFloorPjMonthlyBrl,
        Origin.manual,
        Map.of());
  }

  /** Chave de comparação sem acento, caixa ou espaços extras. */
  public static String key(String value) {
    return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  private static String text(String value, int max, String field) {
    if (value == null) throw new InvalidProfileException(field);
    String stripped = value.strip().replaceAll("\\s+", " ");
    if (stripped.isEmpty() || stripped.length() > max) throw new InvalidProfileException(field);
    return stripped;
  }

  private static List<String> distinct(
      List<String> values, int maxItems, int maxLength, Set<String> taken, String field) {
    if (values == null || values.size() > maxItems) throw new InvalidProfileException(field);
    var result = new LinkedHashMap<String, String>();
    for (String value : values) {
      String item = text(value, maxLength, field);
      if (taken.add(key(item))) result.put(key(item), item);
    }
    return List.copyOf(result.values());
  }

  private static BigDecimal money(BigDecimal value, String field) {
    if (value == null) return null;
    var stripped = value.stripTrailingZeros();
    if (stripped.signum() <= 0 || stripped.scale() > 2) throw new InvalidProfileException(field);
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
