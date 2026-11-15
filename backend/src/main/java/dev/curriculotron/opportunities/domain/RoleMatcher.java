package dev.curriculotron.opportunities.domain;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Axis;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.Field;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.MentionKind;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RoleMatcher {
  private static final Pattern DEVELOPER =
      Pattern.compile(
          "\\b(desenvolvedor(?:a)?|developer|programador(?:a)?|software engineer|engenheir[oa](?:\\(a\\))? de software)\\b");
  private static final Pattern PEOPLE =
      Pattern.compile(
          "(gestao de pessoas|gerir pessoas|manage (?:engineering )?staff|manage (?:the )?team|people management|orcamento|budgets)");

  private enum Family {
    development,
    product,
    design,
    commercial,
    marketing,
    management,
    unknown
  }

  public AxisMatch match(List<VacancyEvidence> evidence, List<String> roles) {
    if (roles.isEmpty())
      return new AxisMatch(Axis.unknown, List.of(), List.of("role_not_configured"));
    var matches = new ArrayList<EvidenceItem>();
    var mismatches = new ArrayList<EvidenceItem>();
    for (var item : evidence) {
      String title = normalize(item.title());
      String text = normalize(item.text());
      String responsibilities = responsibilities(text);
      Family observed = family(title);
      boolean descriptionUsed = false;
      if (responsibilities.matches(
          ".*(?:responsabilidades|responsibilities):.*(?:configurar|gerir|manage|configure).*\\b(?:campanhas|campaigns)\\b.*")) {
        observed = Family.marketing;
        descriptionUsed = true;
      }
      if (observed == Family.development
          && PEOPLE.matcher(responsibilities).find()
          && title.matches(".*\\b(lead|coordenador[a]?|manager)\\b.*")) {
        observed = Family.management;
        descriptionUsed = true;
      }
      if (observed == Family.unknown
          && responsibilities.matches(
              "(?s).*(responsabilidades|responsibilities):.*(desenvolver servicos backend|backend development|desenvolvimento backend).*")) {
        observed = Family.development;
        title += " backend";
        descriptionUsed = true;
      }
      boolean matched = false;
      boolean explicitDifference = false;
      for (String target : roles) {
        String desired = normalize(target);
        Family expected = family(desired);
        if (expected == Family.unknown) {
          if (!title.isBlank() && title.equals(desired)) {
            matched = true;
            matches.add(proof(item, target, false));
            break;
          }
          continue;
        }
        if (observed == Family.unknown) continue;
        if (observed != expected) {
          explicitDifference = true;
          continue;
        }
        String desiredSpecialty = specialty(desired);
        String observedSpecialty = specialty(title);
        if (observedSpecialty.isEmpty()) {
          observedSpecialty = specialty(responsibilities);
          if (!observedSpecialty.isEmpty()) descriptionUsed = true;
        }
        if (expected != Family.development
            || desiredSpecialty.isEmpty()
            || desiredSpecialty.equals(observedSpecialty)) {
          matched = true;
        } else if (!observedSpecialty.isEmpty()) {
          explicitDifference = true;
        }
        if (matched) {
          matches.add(proof(item, target, descriptionUsed));
          break;
        }
      }
      if (!matched && explicitDifference) mismatches.add(proof(item, null, descriptionUsed));
    }
    if (!matches.isEmpty() && !mismatches.isEmpty())
      return new AxisMatch(Axis.unknown, combine(matches, mismatches), List.of("role_conflict"));
    if (!matches.isEmpty()) return new AxisMatch(Axis.matched, matches, List.of("role_match"));
    if (!mismatches.isEmpty())
      return new AxisMatch(Axis.mismatched, mismatches, List.of("role_mismatch"));
    return new AxisMatch(Axis.unknown, List.of(), List.of("role_not_confirmed"));
  }

  private EvidenceItem proof(VacancyEvidence item, String criterion, boolean descriptionUsed) {
    boolean titleKnown = !descriptionUsed && item.title() != null && !item.title().isBlank();
    return new EvidenceItem(
        item.id(),
        item.sourceKind(),
        item.sourceUrl(),
        item.fetchedAt(),
        titleKnown ? Field.title : Field.description,
        null,
        titleKnown ? item.title() : item.text() == null ? "" : item.text(),
        criterion,
        null,
        MentionKind.usage,
        null,
        null);
  }

  private static List<EvidenceItem> combine(List<EvidenceItem> a, List<EvidenceItem> b) {
    var all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }

  private Family family(String value) {
    if (value.matches(".*\\b(product manager|data product|gerente de produto)\\b.*"))
      return Family.product;
    if (value.matches(".*\\b(designer|design de servicos)\\b.*")) return Family.design;
    if (value.matches(".*\\b(relacionamento|vendas|sales representative)\\b.*"))
      return Family.commercial;
    if (value.matches(".*\\b(coordenador[a]?|gerente|manager|director|diretor[a]?)\\b.*"))
      return Family.management;
    if (DEVELOPER.matcher(value).find()) return Family.development;
    return Family.unknown;
  }

  private String specialty(String value) {
    if (value.matches("(?s).*\\bfull[ -]?stack\\b.*")) return "fullstack";
    if (value.matches("(?s).*\\bback[ -]?end\\b.*")) return "backend";
    if (value.matches("(?s).*\\bfront[ -]?end\\b.*")) return "frontend";
    return "";
  }

  private static String responsibilities(String text) {
    var relevant = new ArrayList<String>();
    for (String sentence : text.split("(?<=[.!?])\\s+|[\\r\\n]+")) {
      if (sentence.matches(".*\\b(outra equipe|other team|another team|nao|not|never|sem)\\b.*")) {
        continue;
      }
      if (sentence.matches(
          ".*\\b(responsabilidades|responsibilities|requisitos|requirements|nesta funcao|in this role|you will)\\b.*")) {
        relevant.add(sentence);
      }
    }
    return String.join(" ", relevant);
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .strip();
  }
}
