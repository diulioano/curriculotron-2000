package dev.curriculotron.opportunities.domain;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Field;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TechnologyMatcher {
  private record Term(String name, String version) {}

  public AxisMatch match(List<VacancyEvidence> sources, List<String> technologies) {
    if (technologies.isEmpty())
      return new AxisMatch(Axis.unrestricted, List.of(), List.of("technology_unrestricted"));
    var proof = new ArrayList<EvidenceItem>();
    var positive = new HashSet<String>();
    var negative = new HashSet<String>();
    boolean uncertain = false;
    for (var source : sources) {
      for (String configured : technologies) {
        Term term = term(configured);
        Pattern pattern =
            Pattern.compile(
                "(?<![\\p{L}\\p{N}_+#.])"
                    + (term.name().equals("spring boot")
                        ? "spring\\s*boot"
                        : Pattern.quote(term.name()))
                    + "(?:\\s+(\\d+(?:\\.\\d+)*))?(?![\\p{L}\\p{N}_+#])");
        for (Field field : Field.values()) {
          String raw = field == Field.title ? source.title() : source.text();
          if (raw == null) continue;
          String section = "";
          for (String clause : raw.split("(?<=[.!?])\\s+|[\\r\\n;]+|(?i)\\s+(?:but|mas)\\s+")) {
            section = section(clause, section);
            var matcher = pattern.matcher(normalize(clause));
            while (matcher.find()) {
              MentionKind kind = kind(clause, field, matcher.start(), matcher.end(), section);
              proof.add(
                  new EvidenceItem(
                      source.id(),
                      source.sourceKind(),
                      source.sourceUrl(),
                      source.fetchedAt(),
                      field,
                      section.isEmpty() ? null : section,
                      clause,
                      configured,
                      matcher.group(),
                      kind,
                      matcher.group(1),
                      term.version()));
              if (kind == MentionKind.required
                  || kind == MentionKind.optional
                  || kind == MentionKind.usage) positive.add(term.name());
              if (kind == MentionKind.negated) negative.add(term.name());
              if (kind == MentionKind.uncertain) uncertain = true;
            }
          }
        }
      }
    }
    if (positive.stream().anyMatch(name -> !negative.contains(name)))
      return new AxisMatch(Axis.matched, proof, List.of("technology_match"));
    if (!positive.isEmpty())
      return new AxisMatch(Axis.unknown, proof, List.of("technology_conflict"));
    if (uncertain)
      return new AxisMatch(Axis.unknown, proof, List.of("technology_context_uncertain"));
    boolean complete =
        sources.stream().anyMatch(s -> s.completeness() == VacancyEvidence.Completeness.complete);
    return new AxisMatch(
        complete ? Axis.mismatched : Axis.unknown,
        proof,
        List.of(complete ? "technology_mismatch" : "technology_not_confirmed"));
  }

  private MentionKind kind(String text, Field field, int start, int end, String section) {
    String normalized = normalize(text);
    if (section.equals("company")
        || normalized.matches(
            ".*\\b(outra equipe|other team|another team|outras equipes|our company|nossa empresa)\\b.*"))
      return MentionKind.incidental;
    String before = normalized.substring(0, start);
    String after = normalized.substring(end);
    if (before.matches(".*\\b(nao|not|never|no)\\b(?:\\s+[\\p{L}]+){0,5}\\s*$")
        || after.matches("^\\s+(?:nao|is not|not|isn't)\\b.*")) return MentionKind.negated;
    if (field == Field.title) return MentionKind.usage;
    String context = section + " " + normalized;
    if (context.matches(
        ".*\\b(diferencial|diferenciais|desejavel|preferred|optional|nice to have)\\b.*"))
      return MentionKind.optional;
    if (context.matches(
        ".*\\b(requisitos|required|requirements|experiencia|experience|conhecimento|knowledge|skills)\\b.*"))
      return MentionKind.required;
    if (context.matches(
        ".*\\b(responsabilidades|responsibilities|nesta funcao|in this role|you will)\\b.*"))
      return MentionKind.usage;
    return MentionKind.uncertain;
  }

  private String section(String clause, String previous) {
    String text = normalize(clause).strip();
    if (text.matches(
        "^(about(?: the company| us)?|sobre a empresa|quem somos|benefits|beneficios|company overview|our culture)\\b.*")) {
      return "company";
    }
    var heading =
        Pattern.compile(
                "^(requirements|requisitos|required|responsibilities|responsabilidades|diferenciais|diferencial|preferred|nice to have)\\b(?:\\s*:.*)?$")
            .matcher(text);
    return heading.matches() ? heading.group(1) : previous;
  }

  private Term term(String configured) {
    String normalized = normalize(configured).strip();
    var version = Pattern.compile("^(.*?)(?:\\s+(\\d+(?:\\.\\d+)*))?$").matcher(normalized);
    if (!version.matches()) return new Term(normalized, null);
    String name = version.group(1);
    if (name.equals("springboot")) name = "spring boot";
    return new Term(name, version.group(2));
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
