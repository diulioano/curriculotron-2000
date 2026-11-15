package dev.curriculotron.opportunities.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ranks normalized vacancy facts; inferred requirements and study recommendations are not inputs.
 */
public final class RuleBasedRanker {
  public record Criteria(
      Set<String> roles,
      Set<String> stack,
      Set<String> hybridRegions,
      Set<String> priorityCompanies,
      boolean remotePreferred,
      boolean acceptJunior) {
    public Criteria {
      roles = Set.copyOf(roles);
      stack = Set.copyOf(stack);
      hybridRegions = Set.copyOf(hybridRegions);
      priorityCompanies = Set.copyOf(priorityCompanies);
    }
  }

  public record Input(
      String role,
      String company,
      String workMode,
      String region,
      String seniority,
      String juniorJustification,
      Set<String> requirementFacts,
      ObjectiveFilter.Result salary) {
    public Input {
      requirementFacts = Set.copyOf(requirementFacts);
    }
  }

  public record Result(String fit, int score, List<String> reasons) {
    public Result {
      reasons = List.copyOf(reasons);
    }
  }

  public Result rank(Input job, Criteria criteria) {
    var reasons = new ArrayList<String>();
    reasons.add(job.salary().reason());
    boolean role = contains(criteria.roles(), job.role());
    boolean stack =
        job.requirementFacts().stream().anyMatch(fact -> contains(criteria.stack(), fact));
    boolean remote = "remote".equals(job.workMode());
    boolean hybrid =
        "hybrid".equals(job.workMode()) && contains(criteria.hybridRegions(), job.region());
    boolean unknownLocation = "unknown".equals(job.workMode());
    boolean junior = Set.of("junior", "jr").contains(normalize(job.seniority()));
    boolean justified =
        !junior
            || (criteria.acceptJunior()
                && job.juniorJustification() != null
                && !job.juniorJustification().isBlank());
    if (role) reasons.add("Função compatível com os critérios de busca.");
    if (stack) reasons.add("Tecnologia explícita na vaga coincide com os critérios de busca.");
    if (remote) reasons.add("Trabalho remoto informado.");
    else if (hybrid) reasons.add("Híbrido em região configurada; avaliar deslocamento.");
    else reasons.add("Localização ou modalidade exige revisão do usuário.");
    if (junior)
      reasons.add(
          justified
              ? "Justificativa para júnior: " + job.juniorJustification()
              : "Vaga júnior sem justificativa aprovada nos critérios.");
    boolean priority = contains(criteria.priorityCompanies(), job.company());
    if (priority) reasons.add("Empresa prioritária definida pelo usuário.");
    if (!role
        || !stack
        || !job.salary().eligible()
        || !justified
        || (!remote && !hybrid && !unknownLocation)) {
      return new Result("weak", 0, reasons);
    }
    int score =
        60
            + (priority ? 10 : 0)
            + (remote && criteria.remotePreferred() ? 10 : 0)
            + (!job.salary().confirmationRequired() ? 10 : 0);
    return new Result(unknownLocation ? "medium" : "strong", score, reasons);
  }

  private static boolean contains(Set<String> values, String value) {
    return values.stream().anyMatch(candidate -> normalize(candidate).equals(normalize(value)));
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
