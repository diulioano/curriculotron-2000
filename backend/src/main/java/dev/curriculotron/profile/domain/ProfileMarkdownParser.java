package dev.curriculotron.profile.domain;

import dev.curriculotron.profile.domain.FieldProvenance.Source;
import dev.curriculotron.profile.domain.ProfileDraft.Field;
import dev.curriculotron.profile.domain.ProfileDraft.Warning;
import dev.curriculotron.profile.domain.ProfileDraft.WarningCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lê o perfil de um Markdown com rótulos ({@code Cargo: …}) ou seções ({@code ## Cargo}). Sem
 * rótulos, usa a manchete e o resumo de um currículo e marca esses campos como inferidos. Cada
 * campo preenchido aponta a linha que lhe serviu de evidência. Nada é gravado: o resultado é um
 * rascunho para revisão.
 */
public final class ProfileMarkdownParser {

  private static final Map<String, Field> LABELS = labels();
  private static final Set<Field> SCALARS =
      EnumSet.of(
          Field.role,
          Field.primaryTechnology,
          Field.currentSeniority,
          Field.juniorFloorCltMonthlyBrl,
          Field.juniorFloorPjMonthlyBrl);
  private static final Pattern LABELED = Pattern.compile("^([^:]{1,60}):\\s*(.*)$");
  private static final Pattern HEADING = Pattern.compile("^#{2,6}\\s+(.+)$");
  private static final Pattern LIST_SEPARATOR =
      Pattern.compile("\\s*(?:[,;]|\\s+&\\s+|\\s+e\\s+|\\s+and\\s+)\\s*");
  private static final Pattern SENIORITY =
      Pattern.compile("\\b(junior|jr|pleno|mid[- ]level|mid|senior|sr)\\b");

  private ProfileMarkdownParser() {}

  public static ProfileDraft.Preview parse(String markdown) {
    var raw = new EnumMap<Field, List<String>>(Field.class);
    var source = new EnumMap<Field, Integer>(Field.class);
    String[] lines = markdown.split("\\R");
    Field section = null;
    for (int index = 0; index < lines.length; index++) {
      String text = clean(lines[index]);
      if (text.isEmpty()) continue;
      var heading = HEADING.matcher(text);
      if (heading.matches()) {
        section = LABELS.get(CareerProfile.key(heading.group(1)));
        continue;
      }
      if (text.startsWith("# ")) {
        section = null;
        continue;
      }
      String item = text.replaceFirst("^[-*+]\\s+", "");
      var labeled = LABELED.matcher(item);
      if (labeled.matches() && LABELS.containsKey(CareerProfile.key(labeled.group(1)))) {
        Field field = LABELS.get(CareerProfile.key(labeled.group(1)));
        String value = labeled.group(2);
        // Rótulo sem valor: os itens vêm nas linhas seguintes.
        section = value.isBlank() ? field : null;
        if (!value.isBlank()) add(raw, source, field, value, index);
        continue;
      }
      if (section != null) add(raw, source, section, item, index);
    }
    return build(raw, new Evidence(lines, source));
  }

  private static void add(
      Map<Field, List<String>> raw,
      Map<Field, Integer> source,
      Field field,
      String value,
      int index) {
    raw.computeIfAbsent(field, f -> new ArrayList<>()).add(value);
    source.putIfAbsent(field, index);
  }

  /** Linhas do Markdown e a primeira linha (0-based) que alimentou cada campo rotulado. */
  private record Evidence(String[] lines, Map<Field, Integer> labeled) {
    FieldProvenance at(Source kind, int index) {
      return FieldProvenance.from(kind, index + 1, clean(lines[index]));
    }

    FieldProvenance imported(Field field) {
      return at(Source.imported, labeled.get(field));
    }
  }

  private static ProfileDraft.Preview build(Map<Field, List<String>> raw, Evidence evidence) {
    var warnings = new ArrayList<Warning>();
    var provenance = new EnumMap<Field, FieldProvenance>(Field.class);
    String role = first(raw, Field.role);
    var technologies = new ArrayList<String>();
    String primary = first(raw, Field.primaryTechnology);
    var secondary = list(raw, Field.secondaryTechnologies);
    if (role != null) provenance.put(Field.role, evidence.imported(Field.role));
    if (primary != null)
      provenance.put(Field.primaryTechnology, evidence.imported(Field.primaryTechnology));
    if (!secondary.isEmpty())
      provenance.put(Field.secondaryTechnologies, evidence.imported(Field.secondaryTechnologies));
    var headline = headline(evidence.lines());
    if (role == null && headline.isPresent()) {
      role = headline.get().role();
      warnings.add(inferred(Field.role, "Cargo inferido da manchete do currículo."));
      provenance.put(Field.role, evidence.at(Source.inferred, headline.get().line()));
    }
    if (primary == null && headline.isPresent() && !headline.get().technologies().isEmpty()) {
      var fromHeadline = evidence.at(Source.inferred, headline.get().line());
      primary = headline.get().technologies().getFirst();
      warnings.add(inferred(Field.primaryTechnology, "Tecnologia principal inferida da manchete."));
      provenance.put(Field.primaryTechnology, fromHeadline);
      if (secondary.isEmpty() && headline.get().technologies().size() > 1) {
        secondary = headline.get().technologies().subList(1, headline.get().technologies().size());
        warnings.add(inferred(Field.secondaryTechnologies, "Secundárias inferidas da manchete."));
        provenance.put(Field.secondaryTechnologies, fromHeadline);
      }
    }
    if (primary != null) technologies.add(primary);
    secondary = without(secondary, technologies);
    if (secondary.isEmpty()) provenance.remove(Field.secondaryTechnologies);
    technologies.addAll(secondary);
    var flexible = without(list(raw, Field.flexibleTechnologies), technologies);
    if (!flexible.isEmpty())
      provenance.put(Field.flexibleTechnologies, evidence.imported(Field.flexibleTechnologies));

    Seniority current = null;
    FieldProvenance currentEvidence = null;
    String declared = first(raw, Field.currentSeniority);
    if (declared != null) {
      current = seniority(declared).orElse(null);
      if (current == null)
        warnings.add(invalid(Field.currentSeniority, "Use Júnior, Pleno ou Sênior."));
      else currentEvidence = evidence.imported(Field.currentSeniority);
    } else {
      var found = firstSeniority(evidence.lines());
      if (found.isPresent()) {
        current = found.get().level();
        currentEvidence = evidence.at(Source.inferred, found.get().line());
        warnings.add(inferred(Field.currentSeniority, "Senioridade inferida do texto."));
      }
    }
    if (currentEvidence != null) provenance.put(Field.currentSeniority, currentEvidence);
    var levels = new ArrayList<Seniority>();
    for (String value : list(raw, Field.targetLevels)) {
      var level = seniority(value);
      if (level.isEmpty()) warnings.add(invalid(Field.targetLevels, "Nível alvo não reconhecido."));
      else if (!levels.contains(level.get())) levels.add(level.get());
    }
    if (!levels.isEmpty())
      provenance.put(Field.targetLevels, evidence.imported(Field.targetLevels));
    if (levels.isEmpty() && !raw.containsKey(Field.targetLevels) && currentEvidence != null) {
      levels.add(current);
      warnings.add(inferred(Field.targetLevels, "Níveis alvo iguais à senioridade atual."));
      provenance.put(
          Field.targetLevels,
          new FieldProvenance(Source.inferred, currentEvidence.line(), currentEvidence.excerpt()));
    }
    var clt = money(raw, Field.juniorFloorCltMonthlyBrl, warnings);
    if (clt != null)
      provenance.put(
          Field.juniorFloorCltMonthlyBrl, evidence.imported(Field.juniorFloorCltMonthlyBrl));
    var pj = money(raw, Field.juniorFloorPjMonthlyBrl, warnings);
    if (pj != null)
      provenance.put(
          Field.juniorFloorPjMonthlyBrl, evidence.imported(Field.juniorFloorPjMonthlyBrl));
    var priorities = list(raw, Field.priorities);
    if (!priorities.isEmpty())
      provenance.put(Field.priorities, evidence.imported(Field.priorities));

    if (role == null) warnings.add(missing(Field.role, "Informe o cargo."));
    if (primary == null)
      warnings.add(missing(Field.primaryTechnology, "Informe a tecnologia principal."));
    if (current == null && declared == null)
      warnings.add(missing(Field.currentSeniority, "Informe a senioridade atual."));
    if (levels.isEmpty() && !raw.containsKey(Field.targetLevels))
      warnings.add(missing(Field.targetLevels, "Informe os níveis alvo."));
    if (clt == null && !raw.containsKey(Field.juniorFloorCltMonthlyBrl))
      warnings.add(
          missing(
              Field.juniorFloorCltMonthlyBrl, "Sem piso júnior CLT, vaga júnior CLT é excluída."));
    if (pj == null && !raw.containsKey(Field.juniorFloorPjMonthlyBrl))
      warnings.add(
          missing(Field.juniorFloorPjMonthlyBrl, "Sem piso júnior PJ, vaga júnior PJ é excluída."));

    return new ProfileDraft.Preview(
        new ProfileDraft(role, primary, secondary, flexible, current, levels, priorities, clt, pj),
        warnings,
        provenance);
  }

  private record Headline(String role, List<String> technologies, int line) {}

  /** Linha {@code **Cargo | Tec1, Tec2 & Tec3**} logo abaixo do título do currículo. */
  private static Optional<Headline> headline(String[] lines) {
    boolean afterTitle = false;
    for (int index = 0; index < lines.length; index++) {
      String text = lines[index].strip();
      if (text.startsWith("# ")) {
        afterTitle = true;
        continue;
      }
      if (!afterTitle || text.isEmpty()) continue;
      if (text.startsWith("#")) return Optional.empty();
      String cleaned = clean(text);
      int bar = cleaned.indexOf('|');
      if (!text.startsWith("**") || bar <= 0) continue;
      String role = cleaned.substring(0, bar).strip();
      if (role.isEmpty() || role.length() > 200) return Optional.empty();
      return Optional.of(new Headline(role, split(cleaned.substring(bar + 1)), index));
    }
    return Optional.empty();
  }

  private record Found(Seniority level, int line) {}

  /** Primeira menção de senioridade e a linha (0-based) em que aparece. */
  private static Optional<Found> firstSeniority(String[] lines) {
    for (int index = 0; index < lines.length; index++) {
      var matcher = SENIORITY.matcher(CareerProfile.key(lines[index]));
      if (matcher.find()) {
        int line = index;
        return seniority(matcher.group(1)).map(level -> new Found(level, line));
      }
    }
    return Optional.empty();
  }

  private static Optional<Seniority> seniority(String value) {
    return switch (CareerProfile.key(value).replace('-', ' ')) {
      case "junior", "jr" -> Optional.of(Seniority.junior);
      case "pleno", "pl", "mid", "mid level" -> Optional.of(Seniority.pleno);
      case "senior", "sr" -> Optional.of(Seniority.senior);
      default -> Optional.empty();
    };
  }

  private static BigDecimal money(Map<Field, List<String>> raw, Field field, List<Warning> out) {
    String value = first(raw, field);
    if (value == null) return null;
    String digits = value.replaceAll("(?i)r\\$|\\s", "");
    if (digits.contains(",")) digits = digits.replace(".", "").replace(',', '.');
    else if (digits.matches("\\d{1,3}(\\.\\d{3})+")) digits = digits.replace(".", "");
    try {
      var amount = new BigDecimal(digits).stripTrailingZeros();
      if (amount.signum() > 0 && amount.scale() <= 2)
        return amount.scale() < 0 ? amount.setScale(0) : amount;
    } catch (NumberFormatException ignored) {
      // tratado abaixo como valor inválido
    }
    out.add(invalid(field, "Valor mensal em reais não reconhecido."));
    return null;
  }

  private static String first(Map<Field, List<String>> raw, Field field) {
    var values = list(raw, field);
    return values.isEmpty() ? null : values.getFirst();
  }

  private static List<String> list(Map<Field, List<String>> raw, Field field) {
    var result = new LinkedHashMap<String, String>();
    for (String value : raw.getOrDefault(field, List.of())) {
      var parts = SCALARS.contains(field) ? List.of(value.strip()) : split(value);
      for (String part : parts) {
        if (!part.isEmpty() && part.length() <= 200)
          result.putIfAbsent(CareerProfile.key(part), part);
      }
    }
    return List.copyOf(result.values());
  }

  private static List<String> without(List<String> values, List<String> taken) {
    var keys = taken.stream().map(CareerProfile::key).toList();
    return values.stream().filter(value -> !keys.contains(CareerProfile.key(value))).toList();
  }

  private static List<String> split(String value) {
    return LIST_SEPARATOR
        .splitAsStream(value)
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static String clean(String line) {
    return line.replace("**", "").replace("__", "").replaceAll("\\s+", " ").strip();
  }

  private static Warning missing(Field field, String message) {
    return new Warning(field, WarningCode.missing, message);
  }

  private static Warning inferred(Field field, String message) {
    return new Warning(field, WarningCode.inferred, message);
  }

  private static Warning invalid(Field field, String message) {
    return new Warning(field, WarningCode.invalid, message);
  }

  private static Map<String, Field> labels() {
    var map = new LinkedHashMap<String, Field>();
    for (String label : List.of("cargo", "cargo alvo", "funcao", "role"))
      map.put(label, Field.role);
    for (String label :
        List.of("tecnologia principal", "stack principal", "principal", "primary technology"))
      map.put(label, Field.primaryTechnology);
    for (String label : List.of("tecnologias secundarias", "secundarias", "secondary technologies"))
      map.put(label, Field.secondaryTechnologies);
    for (String label : List.of("tecnologias flexiveis", "flexiveis", "flexible technologies"))
      map.put(label, Field.flexibleTechnologies);
    for (String label :
        List.of("senioridade atual", "senioridade", "nivel atual", "current seniority"))
      map.put(label, Field.currentSeniority);
    for (String label : List.of("niveis alvo", "nivel alvo", "target levels"))
      map.put(label, Field.targetLevels);
    for (String label : List.of("prioridades", "priorities")) map.put(label, Field.priorities);
    for (String label : List.of("piso junior clt", "junior floor clt"))
      map.put(label, Field.juniorFloorCltMonthlyBrl);
    for (String label : List.of("piso junior pj", "junior floor pj"))
      map.put(label, Field.juniorFloorPjMonthlyBrl);
    return Map.copyOf(map);
  }
}
