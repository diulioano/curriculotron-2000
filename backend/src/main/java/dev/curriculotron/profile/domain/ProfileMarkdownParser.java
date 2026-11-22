package dev.curriculotron.profile.domain;

import dev.curriculotron.profile.domain.FieldProvenance.Source;
import dev.curriculotron.profile.domain.ProfileDraft.Field;
import dev.curriculotron.profile.domain.ProfileDraft.Warning;
import dev.curriculotron.profile.domain.ProfileDraft.WarningCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lê o perfil a partir de texto de currículo (extraído de PDF, Markdown ou texto livre). Suporta
 * campos rotulados ({@code Cargo: …}), seções ({@code ## Cargo}) e currículos reais com seções como
 * Resumo Profissional, Experiência Profissional e Habilidades Técnicas. Tudo o que é deduzido fica
 * marcado como {@link Source#inferred} com a linha de evidência. Nada é gravado: o resultado é um
 * rascunho para revisão.
 */
public final class ProfileMarkdownParser {

  private enum ResumeSection {
    SUMMARY,
    EXPERIENCE,
    SKILLS,
    OTHER
  }

  private static final Map<String, Field> LABELS = labels();
  private static final Map<String, ResumeSection> RESUME_SECTIONS = resumeSections();
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
      Pattern.compile("\\s*(?:[,;|•]|\\s+&\\s+|\\s+e\\s+|\\s+and\\s+)\\s*");
  private static final Pattern SENIORITY =
      Pattern.compile("\\b(junior|jr|pleno|mid[- ]level|mid|senior|sr)\\b");
  private static final Pattern YEARS_OF_EXPERIENCE =
      Pattern.compile(
          "(?:mais\\s+de\\s+|more\\s+than\\s+|over\\s+)?(\\d{1,2})\\+?\\s*(?:anos?|years?)\\s+(?:de\\s+experiencia|of\\s+experience)\\b");
  private static final Pattern ROLE_LINE =
      Pattern.compile(
          "(?i)\\b(desenvolvedor(?:a)?|engenheir[oa]|programador(?:a)?|arquiteto(?:a)?|analista|especialista|tech\\s+lead|software\\s+(?:developer|engineer)|(?:back[\\s-]?end|front[\\s-]?end|full[\\s-]?stack)\\s+(?:developer|engineer)|devops|sre)\\b");
  private static final Pattern ROLE_PHRASE =
      Pattern.compile(
          "(?i)\\b((?:desenvolvedor(?:a)?|engenheir[oa]|programador(?:a)?|arquiteto(?:a)?|analista|especialista|tech\\s+lead|software\\s+(?:developer|engineer)|(?:back[\\s-]?end|front[\\s-]?end|full[\\s-]?stack)\\s+(?:developer|engineer))(?:\\s+(?:de\\s+software|de\\s+sistemas|de\\s+dados|back[\\s-]?end|front[\\s-]?end|full[\\s-]?stack|web|mobile|cloud|devops|java|python|kotlin|node\\.?js|typescript|javascript|react|angular|\\.net|c#|go|golang|php|ruby)){0,3})");
  private static final Pattern ACTIVITY_VERB_PREFIX =
      Pattern.compile(
          "(?i)^(?:desenvolvi|atuei|participei|construi|implementei|apliquei|apoiei|liderei|criei|mantive|otimizei|colaborei|trabalhei|responsavel|atuacao|experiencia)\\b");
  private static final Pattern TRAILING_VERSION = Pattern.compile("\\s+\\d+(?:\\.\\d+)*$");
  private static final Set<String> SKILL_CATEGORY_PREFIXES =
      Set.of(
          "backend",
          "back-end",
          "frontend",
          "front-end",
          "linguagens",
          "languages",
          "frameworks",
          "banco de dados",
          "bancos de dados",
          "databases",
          "cloud",
          "devops",
          "ferramentas",
          "tools",
          "arquitetura",
          "metodologias",
          "testes",
          "outros",
          "other",
          "mensageria");
  private static final List<String> KNOWN_TECHNOLOGIES =
      List.of(
          "Spring Boot",
          "Java",
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
          "Sybase",
          "SQL Server",
          "MongoDB",
          "Redis",
          "Elasticsearch",
          "GraphQL",
          "Quarkus",
          "Hibernate",
          "JUnit",
          "Mockito",
          "Jenkins",
          "Git",
          "Linux",
          "CI/CD",
          "REST APIs",
          "Microsserviços",
          "SQL",
          "NoSQL");

  private ProfileMarkdownParser() {}

  public static ProfileDraft.Preview parse(String markdown) {
    var raw = new EnumMap<Field, List<String>>(Field.class);
    var source = new EnumMap<Field, Integer>(Field.class);
    var resumeLines = new EnumMap<ResumeSection, List<IndexedLine>>(ResumeSection.class);
    String[] lines = markdown.split("\\R");
    Field section = null;
    ResumeSection resumeSection = null;
    for (int index = 0; index < lines.length; index++) {
      String text = clean(lines[index]);
      if (text.isEmpty()) continue;
      var heading = HEADING.matcher(text);
      if (heading.matches()) {
        String headingTitle = heading.group(1);
        Field labeledSection = LABELS.get(CareerProfile.key(headingTitle));
        if (labeledSection != null) {
          section = labeledSection;
          resumeSection = null;
          continue;
        }
        ResumeSection detected = detectResumeSection(headingTitle);
        if (detected != null) {
          section = null;
          resumeSection = detected;
          continue;
        }
        // Em currículos convertidos de PDF, subtítulos de cargo podem vir com `## _Cargo_`.
        if (resumeSection == ResumeSection.EXPERIENCE) {
          resumeLines
              .computeIfAbsent(resumeSection, s -> new ArrayList<>())
              .add(new IndexedLine(index, cleanInline(headingTitle)));
          continue;
        }
        section = null;
        resumeSection = null;
        continue;
      }
      if (text.startsWith("# ")) {
        section = null;
        resumeSection = null;
        continue;
      }
      ResumeSection plainSection = detectPlainSectionHeading(text);
      if (plainSection != null) {
        section = null;
        resumeSection = plainSection;
        continue;
      }
      String item = stripBullet(text);
      var labeled = LABELED.matcher(item);
      if (labeled.matches() && LABELS.containsKey(CareerProfile.key(labeled.group(1)))) {
        Field field = LABELS.get(CareerProfile.key(labeled.group(1)));
        String value = labeled.group(2);
        // Rótulo sem valor: os itens vêm nas linhas seguintes.
        section = value.isBlank() ? field : null;
        resumeSection = null;
        if (!value.isBlank()) add(raw, source, field, value, index);
        continue;
      }
      if (section != null) {
        add(raw, source, section, item, index);
      } else if (resumeSection != null) {
        resumeLines
            .computeIfAbsent(resumeSection, s -> new ArrayList<>())
            .add(new IndexedLine(index, text));
      }
    }
    return build(raw, resumeLines, new Evidence(lines, source));
  }

  private record IndexedLine(int line, String text) {}

  private static void add(
      Map<Field, List<String>> raw,
      Map<Field, Integer> source,
      Field field,
      String value,
      int index) {
    raw.computeIfAbsent(field, f -> new ArrayList<>()).add(value);
    source.putIfAbsent(field, index);
  }

  /** Linhas do documento e a primeira linha (0-based) que alimentou cada campo rotulado. */
  private record Evidence(String[] lines, Map<Field, Integer> labeled) {
    FieldProvenance at(Source kind, int index) {
      return FieldProvenance.from(kind, index + 1, cleanInline(clean(lines[index])));
    }

    FieldProvenance imported(Field field) {
      return at(Source.imported, labeled.get(field));
    }
  }

  private static ProfileDraft.Preview build(
      Map<Field, List<String>> raw,
      Map<ResumeSection, List<IndexedLine>> resumeLines,
      Evidence evidence) {
    var warnings = new ArrayList<Warning>();
    var provenance = new EnumMap<Field, FieldProvenance>(Field.class);
    String role = first(raw, Field.role);
    var technologies = new ArrayList<String>();
    String primary = first(raw, Field.primaryTechnology);
    var secondary = list(raw, Field.secondaryTechnologies);
    boolean hasExplicitTechnologies = primary != null || !secondary.isEmpty();

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
    if (role == null) {
      var inferredRole = inferRoleFromResume(resumeLines, evidence.lines());
      if (inferredRole.isPresent()) {
        role = inferredRole.get().value();
        warnings.add(inferred(Field.role, "Cargo inferido do currículo — confira."));
        provenance.put(Field.role, evidence.at(Source.inferred, inferredRole.get().line()));
      }
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

    if (!hasExplicitTechnologies && primary == null) {
      var fromSkills = inferTechnologiesFromSkills(resumeLines, role);
      if (fromSkills.isPresent()) {
        var found = fromSkills.get();
        primary = found.primary();
        secondary = found.secondary();
        var skillEvidence = evidence.at(Source.inferred, found.line());
        warnings.add(
            inferred(
                Field.primaryTechnology,
                "Tecnologia principal inferida das habilidades do currículo."));
        provenance.put(Field.primaryTechnology, skillEvidence);
        if (!secondary.isEmpty()) {
          warnings.add(
              inferred(
                  Field.secondaryTechnologies,
                  "Tecnologias secundárias inferidas das habilidades do currículo."));
          provenance.put(Field.secondaryTechnologies, skillEvidence);
        }
      } else {
        var fromText = inferTechnologiesFromFreeText(evidence.lines(), role);
        if (fromText.isPresent()) {
          var found = fromText.get();
          primary = found.primary();
          secondary = found.secondary();
          var textEvidence = evidence.at(Source.inferred, found.line());
          warnings.add(
              inferred(Field.primaryTechnology, "Tecnologia principal inferida do texto."));
          provenance.put(Field.primaryTechnology, textEvidence);
          if (!secondary.isEmpty()) {
            warnings.add(
                inferred(
                    Field.secondaryTechnologies, "Tecnologias secundárias inferidas do texto."));
            provenance.put(Field.secondaryTechnologies, textEvidence);
          }
        }
      }
    }

    if (primary != null) technologies.add(primary);
    secondary = without(secondary, technologies);
    if (secondary.size() > CareerProfile.MAX_TECHNOLOGIES) {
      secondary = secondary.subList(0, CareerProfile.MAX_TECHNOLOGIES);
    }
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
      } else {
        var byYears = seniorityFromYears(evidence.lines());
        if (byYears.isPresent()) {
          current = byYears.get().level();
          currentEvidence = evidence.at(Source.inferred, byYears.get().line());
          warnings.add(
              inferred(
                  Field.currentSeniority,
                  "Senioridade estimada pelos anos de experiência — confira."));
        }
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

  private record InferredValue(String value, int line) {}

  private record InferredTechs(String primary, List<String> secondary, int line) {}

  private static Optional<InferredValue> inferRoleFromResume(
      Map<ResumeSection, List<IndexedLine>> resumeLines, String[] allLines) {
    // 1. Cargo atual/mais recente na seção de Experiência Profissional.
    for (var entry : resumeLines.getOrDefault(ResumeSection.EXPERIENCE, List.of())) {
      String rawText = entry.text();
      if (isBullet(rawText)) continue;
      String candidate = cleanInline(rawText);
      if (candidate.isEmpty() || candidate.length() > 90) continue;
      if (ACTIVITY_VERB_PREFIX.matcher(CareerProfile.key(candidate)).find()) continue;
      if (ROLE_LINE.matcher(candidate).find()) {
        return Optional.of(new InferredValue(candidate, entry.line()));
      }
    }
    // 2. Cargo citado no Resumo Profissional.
    for (var entry : resumeLines.getOrDefault(ResumeSection.SUMMARY, List.of())) {
      var found = extractRolePhrase(entry.text());
      if (found.isPresent()) return Optional.of(new InferredValue(found.get(), entry.line()));
    }
    // 3. Texto livre (sem seções): procura frase de cargo nas linhas do texto.
    for (int index = 0; index < allLines.length; index++) {
      String cleaned = cleanInline(clean(allLines[index]));
      if (cleaned.isEmpty() || cleaned.startsWith("#")) continue;
      if (cleaned.contains("@") || cleaned.contains("http")) continue;
      var found = extractRolePhrase(cleaned);
      if (found.isPresent()) return Optional.of(new InferredValue(found.get(), index));
    }
    return Optional.empty();
  }

  private static Optional<String> extractRolePhrase(String text) {
    String cleaned = cleanInline(text);
    var matcher = ROLE_PHRASE.matcher(cleaned);
    if (!matcher.find()) return Optional.empty();
    String phrase = matcher.group(1).strip();
    if (phrase.isEmpty() || phrase.length() > 200) return Optional.empty();
    return Optional.of(capitalizeFirst(phrase));
  }

  private static Optional<InferredTechs> inferTechnologiesFromSkills(
      Map<ResumeSection, List<IndexedLine>> resumeLines, String role) {
    var skillLines = resumeLines.getOrDefault(ResumeSection.SKILLS, List.of());
    if (skillLines.isEmpty()) return Optional.empty();
    var unique = new LinkedHashMap<String, String>();
    int firstLine = -1;
    for (var entry : skillLines) {
      String text = stripCategoryPrefix(stripBullet(cleanInline(entry.text())));
      for (String rawItem : splitSkillItems(text)) {
        String item = normalizeTechnologyItem(rawItem);
        if (item.isEmpty() || item.length() > 100) continue;
        String base = technologyBaseKey(item);
        if (base.isEmpty()) continue;
        if (firstLine < 0) firstLine = entry.line();
        unique.putIfAbsent(base, item);
      }
    }
    if (unique.isEmpty()) return Optional.empty();
    return Optional.of(pickPrimaryAndSecondary(List.copyOf(unique.values()), role, firstLine));
  }

  private static Optional<InferredTechs> inferTechnologiesFromFreeText(
      String[] lines, String role) {
    record Hit(String tech, int line, int position) {}
    var found = new LinkedHashMap<String, Hit>();
    for (int index = 0; index < lines.length; index++) {
      String text = cleanInline(clean(lines[index]));
      if (text.isEmpty() || text.startsWith("#")) continue;
      String normalized = CareerProfile.key(text).replace("ci/ cd", "ci/cd");
      for (String tech : KNOWN_TECHNOLOGIES) {
        String base = technologyBaseKey(tech);
        if (found.containsKey(base)) continue;
        int pos = findWholeTerm(normalized, CareerProfile.key(tech));
        if (pos >= 0) {
          found.put(base, new Hit(tech, index, pos));
        }
      }
    }
    if (found.isEmpty()) return Optional.empty();
    var ordered =
        found.values().stream()
            .sorted(Comparator.comparingInt(Hit::line).thenComparingInt(Hit::position))
            .toList();
    var list = ordered.stream().map(Hit::tech).toList();
    return Optional.of(pickPrimaryAndSecondary(list, role, ordered.getFirst().line()));
  }

  private static InferredTechs pickPrimaryAndSecondary(
      List<String> items, String role, int evidenceLine) {
    String primary = items.getFirst();
    if (role != null && !role.isBlank()) {
      String roleKey = CareerProfile.key(role);
      for (String item : items) {
        if (findWholeTerm(roleKey, CareerProfile.key(item)) >= 0) {
          primary = item;
          break;
        }
      }
    }
    String chosenPrimary = primary;
    String chosenBase = technologyBaseKey(chosenPrimary);
    var secondary =
        items.stream()
            .filter(item -> !technologyBaseKey(item).equals(chosenBase))
            .limit(CareerProfile.MAX_TECHNOLOGIES)
            .toList();
    return new InferredTechs(chosenPrimary, secondary, evidenceLine);
  }

  private static int findWholeTerm(String haystack, String needle) {
    if (needle.isEmpty()) return -1;
    int from = 0;
    while (from <= haystack.length() - needle.length()) {
      int idx = haystack.indexOf(needle, from);
      if (idx < 0) return -1;
      boolean leftOk = idx == 0 || !Character.isLetterOrDigit(haystack.charAt(idx - 1));
      int end = idx + needle.length();
      boolean rightOk =
          end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
      if (leftOk && rightOk) return idx;
      from = idx + 1;
    }
    return -1;
  }

  private static List<String> splitSkillItems(String text) {
    return LIST_SEPARATOR.splitAsStream(text).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }

  private static String stripCategoryPrefix(String text) {
    int colon = text.indexOf(':');
    if (colon <= 0 || colon > 30) return text;
    String prefix = CareerProfile.key(text.substring(0, colon));
    return SKILL_CATEGORY_PREFIXES.contains(prefix) ? text.substring(colon + 1).strip() : text;
  }

  private static String normalizeTechnologyItem(String item) {
    return item.replaceAll("\\s*/\\s*", "/").replaceAll("\\s+", " ").strip();
  }

  /** Normaliza versão final ("Java 17" -> "java") para evitar duplicação na lista extraída. */
  private static String technologyBaseKey(String item) {
    return TRAILING_VERSION.matcher(CareerProfile.key(item)).replaceFirst("").strip();
  }

  private static ResumeSection detectPlainSectionHeading(String text) {
    if (text.length() > 65 || isBullet(text)) return null;
    if (text.contains(".") && !text.endsWith(":")) return null;
    return detectResumeSection(text);
  }

  private static ResumeSection detectResumeSection(String heading) {
    String cleaned = cleanInline(heading).replaceFirst(":$", "").strip();
    if (cleaned.isEmpty()) return null;
    String key = CareerProfile.key(cleaned);
    ResumeSection direct = RESUME_SECTIONS.get(key);
    if (direct != null) return direct;
    String collapsed = collapseSpacedLetters(key);
    return collapsed == null ? null : RESUME_SECTIONS.get(collapsed);
  }

  /**
   * Colapsa cabeçalhos com letras espaçadas comuns em PDFs (ex.: {@code "r e s u m o p r o f i s s
   * i o n a l"} -> {@code "resumoprofissional"}).
   */
  private static String collapseSpacedLetters(String normalized) {
    String[] tokens = normalized.split(" ");
    if (tokens.length < 4) return null;
    int singleChars = 0;
    for (String token : tokens) {
      if (token.length() == 1) singleChars++;
    }
    if (singleChars * 2 < tokens.length) return null;
    return String.join("", tokens);
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

  /** Estima senioridade quando o texto menciona anos de experiência sem nível explícito. */
  private static Optional<Found> seniorityFromYears(String[] lines) {
    for (int index = 0; index < lines.length; index++) {
      var matcher = YEARS_OF_EXPERIENCE.matcher(CareerProfile.key(lines[index]));
      if (matcher.find()) {
        int years = Integer.parseInt(matcher.group(1));
        Seniority level =
            years >= 6 ? Seniority.senior : years >= 3 ? Seniority.pleno : Seniority.junior;
        return Optional.of(new Found(level, index));
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

  private static boolean isBullet(String text) {
    return text.startsWith("- ")
        || text.startsWith("* ")
        || text.startsWith("+ ")
        || text.startsWith("• ");
  }

  private static String stripBullet(String text) {
    return text.replaceFirst("^(?:[-*+•]\\s+)+", "").strip();
  }

  private static String clean(String line) {
    return line.replace("**", "").replace("__", "").replaceAll("\\s+", " ").strip();
  }

  private static String cleanInline(String text) {
    String stripped = text.strip();
    if (stripped.length() >= 2
        && ((stripped.startsWith("_") && stripped.endsWith("_"))
            || (stripped.startsWith("*") && stripped.endsWith("*")))) {
      stripped = stripped.substring(1, stripped.length() - 1).strip();
    }
    return stripped.replaceAll("\\s+", " ").strip();
  }

  private static String capitalizeFirst(String text) {
    if (text.isEmpty()) return text;
    return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
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

  private static Map<String, ResumeSection> resumeSections() {
    var map = new LinkedHashMap<String, ResumeSection>();
    for (String key :
        List.of(
            "resumo",
            "resumo profissional",
            "resumoprofissional",
            "sobre",
            "sobre mim",
            "sobremim",
            "perfil profissional",
            "perfilprofissional",
            "objetivo",
            "summary",
            "professional summary",
            "professionalsummary",
            "about",
            "about me",
            "aboutme")) {
      map.put(key, ResumeSection.SUMMARY);
    }
    for (String key :
        List.of(
            "experiencia",
            "experiencias",
            "experiencia profissional",
            "experienciaprofissional",
            "historico profissional",
            "historicoprofissional",
            "experience",
            "work experience",
            "workexperience",
            "professional experience",
            "professionalexperience")) {
      map.put(key, ResumeSection.EXPERIENCE);
    }
    for (String key :
        List.of(
            "habilidades",
            "habilidades tecnicas",
            "habilidadestecnicas",
            "competencias",
            "competencias tecnicas",
            "competenciastecnicas",
            "tecnologias",
            "conhecimentos",
            "conhecimentos tecnicos",
            "conhecimentostecnicos",
            "skills",
            "technical skills",
            "technicalskills",
            "tech stack",
            "techstack",
            "stack")) {
      map.put(key, ResumeSection.SKILLS);
    }
    for (String key :
        List.of(
            "formacao",
            "formacao academica",
            "formacaoacademica",
            "educacao",
            "education",
            "idiomas",
            "languages",
            "certificacoes",
            "certifications",
            "cursos",
            "courses",
            "projetos",
            "projects")) {
      map.put(key, ResumeSection.OTHER);
    }
    return Map.copyOf(map);
  }
}
