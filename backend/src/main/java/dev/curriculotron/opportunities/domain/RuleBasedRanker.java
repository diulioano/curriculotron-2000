package dev.curriculotron.opportunities.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

  /**
   * @param juniorException decisão da exceção júnior quando o nível usado pelo ranking é júnior e
   *     está fora dos níveis alvo do perfil; {@code null} quando não se aplica ou sem perfil
   */
  public record Result(
      String fit, int score, List<String> reasons, JuniorExceptionPolicy.Decision juniorException) {
    public Result {
      reasons = List.copyOf(reasons);
    }

    public Result(String fit, int score, List<String> reasons) {
      this(fit, score, reasons, null);
    }
  }

  /**
   * Perfil de carreira (US006) usado no ranking: peso da tecnologia principal/secundária, níveis
   * alvo e pisos próprios para a exceção júnior. Valores de nível: {@code junior}, {@code pleno},
   * {@code senior}.
   */
  public record Profile(
      String primaryTechnology,
      Set<String> secondaryTechnologies,
      String currentSeniority,
      Set<String> targetLevels,
      BigDecimal juniorFloorClt,
      BigDecimal juniorFloorPj) {
    public Profile {
      Objects.requireNonNull(primaryTechnology);
      secondaryTechnologies = Set.copyOf(secondaryTechnologies);
      targetLevels = Set.copyOf(targetLevels);
    }
  }

  /**
   * Salário anunciado na vaga, sem conversão; {@code min} é o valor garantido pelo anúncio e {@code
   * max} só explica faixas que cruzam o piso.
   */
  public record AnnouncedSalary(String contract, String unit, BigDecimal min, BigDecimal max) {
    public AnnouncedSalary(String contract, String unit, BigDecimal min) {
      this(contract, unit, min, null);
    }
  }

  static final int PRIMARY_BONUS = 15;
  static final int SECONDARY_BONUS = 8;

  public Result rank(Input job, Criteria criteria) {
    return rank(
        job,
        criteria,
        contains(criteria.roles(), job.role()),
        job.requirementFacts().stream().anyMatch(fact -> contains(criteria.stack(), fact)),
        false,
        Set.of(),
        null,
        null);
  }

  public Result rank(Input job, Criteria criteria, CompatibilityEvaluator.Result compatibility) {
    return rank(job, criteria, compatibility, null, null);
  }

  /**
   * @param profile perfil salvo, ou {@code null} para a regra anterior (júnior só com
   *     justificativa)
   */
  public Result rank(
      Input job,
      Criteria criteria,
      CompatibilityEvaluator.Result compatibility,
      Profile profile,
      AnnouncedSalary announced) {
    var technology = compatibility.technology();
    var result =
        rank(
            job,
            criteria,
            compatibility.role().status() == CompatibilityAssessment.Axis.matched,
            technology.status() == CompatibilityAssessment.Axis.matched
                || technology.status() == CompatibilityAssessment.Axis.unrestricted,
            technology.status() == CompatibilityAssessment.Axis.unrestricted,
            confirmedTechnologies(technology.evidence()),
            profile,
            announced);
    if ("strong".equals(result.fit())
        && compatibility.role().reasons().contains("role_partial_specialty")) {
      var reasons = new ArrayList<>(result.reasons());
      reasons.add(
          "A vaga cobre parte do trabalho Full Stack e usa a tecnologia principal do perfil.");
      return new Result("medium", result.score(), reasons, result.juniorException());
    }
    return result;
  }

  private Result rank(
      Input job,
      Criteria criteria,
      boolean role,
      boolean stack,
      boolean unrestricted,
      Set<String> confirmed,
      Profile profile,
      AnnouncedSalary announced) {
    var junior =
        profile == null ? null : juniorDecision(job, criteria, role && stack, profile, announced);
    boolean salaryEligible = junior == null ? job.salary().eligible() : junior.salary().confirmed();
    boolean salaryConfirmed =
        junior == null ? !job.salary().confirmationRequired() : junior.salary().confirmed();
    var reasons = new ArrayList<String>();
    reasons.add(
        junior == null
            ? job.salary().reason()
            : junior.salary().confirmed()
                ? "Remuneração compatível com o piso júnior do perfil."
                : "Remuneração anunciada não confirma o piso júnior do perfil.");
    boolean remote = "remote".equals(job.workMode());
    boolean hybrid =
        "hybrid".equals(job.workMode()) && contains(criteria.hybridRegions(), job.region());
    boolean unknownLocation = "unknown".equals(job.workMode());
    if (role) reasons.add("Função compatível com os critérios de busca.");
    if (stack)
      reasons.add(
          unrestricted
              ? "Critérios sem restrição tecnológica."
              : "Tecnologia explícita na vaga coincide com os critérios de busca.");
    if (remote) reasons.add("Trabalho remoto informado.");
    else if (hybrid) reasons.add("Híbrido em região configurada; avaliar deslocamento.");
    else reasons.add("Localização ou modalidade exige revisão do usuário.");
    boolean levelAccepted =
        profile == null
            ? legacyJunior(job, criteria, reasons)
            : levelAccepted(job, profile, junior, reasons);
    if (contains(criteria.priorityCompanies(), job.company()))
      reasons.add("Empresa prioritária definida pelo usuário (desempate).");
    if (!role
        || !stack
        || !salaryEligible
        || !levelAccepted
        || (!remote && !hybrid && !unknownLocation)) {
      return new Result("weak", 0, reasons, junior);
    }
    int score =
        60
            + (remote && criteria.remotePreferred() ? 10 : 0)
            + (salaryConfirmed ? 10 : 0)
            + stackBonus(profile, confirmed, reasons);
    return new Result(unknownLocation ? "medium" : "strong", score, reasons, junior);
  }

  private static boolean legacyJunior(Input job, Criteria criteria, List<String> reasons) {
    if (!"junior".equals(level(job.seniority()))) return true;
    boolean justified = criteria.acceptJunior() && hasText(job.juniorJustification());
    reasons.add(
        justified
            ? "Justificativa para júnior: " + job.juniorJustification()
            : "Vaga júnior sem justificativa aprovada nos critérios.");
    return justified;
  }

  /** Exceção júnior apenas para o nível usado pelo ranking e fora dos níveis alvo do perfil. */
  private static JuniorExceptionPolicy.Decision juniorDecision(
      Input job, Criteria criteria, boolean fit, Profile profile, AnnouncedSalary announced) {
    if (!"junior".equals(level(job.seniority())) || profile.targetLevels().contains("junior"))
      return null;
    return JuniorExceptionPolicy.decide(
        fit, profile, announced, criteria.acceptJunior(), job.juniorJustification());
  }

  private static boolean levelAccepted(
      Input job, Profile profile, JuniorExceptionPolicy.Decision junior, List<String> reasons) {
    String level = level(job.seniority());
    if (level == null) {
      reasons.add("Nível da vaga não confirmado no anúncio.");
      return true;
    }
    if (junior == null) {
      if (profile.targetLevels().contains(level)) {
        reasons.add(
            level.equals(profile.currentSeniority())
                ? "O nível da vaga coincide com sua senioridade atual e está entre os níveis buscados."
                : "Nível "
                    + level
                    + " entre os níveis buscados. Sua senioridade atual é "
                    + profile.currentSeniority()
                    + ".");
        return true;
      }
      reasons.add("Nível " + level + " fora dos níveis alvo do perfil.");
      return false;
    }
    if (junior.qualified()) {
      reasons.add("Exceção júnior: " + junior.justification().text());
      return true;
    }
    reasons.add("Vaga júnior excluída; falta " + String.join(", ", junior.missing()) + ".");
    return false;
  }

  private static int stackBonus(Profile profile, Set<String> confirmed, List<String> reasons) {
    if (profile == null) return 0;
    if (confirmed.contains(StudyPreparation.base(profile.primaryTechnology()))) {
      reasons.add("Tecnologia principal do perfil na vaga.");
      return PRIMARY_BONUS;
    }
    if (profile.secondaryTechnologies().stream()
        .anyMatch(t -> confirmed.contains(StudyPreparation.base(t)))) {
      reasons.add("Tecnologia secundária do perfil na vaga.");
      return SECONDARY_BONUS;
    }
    return 0;
  }

  /** Critérios citados como obrigatórios, diferenciais ou uso, sem negação na mesma vaga. */
  static Set<String> confirmedTechnologies(List<CompatibilityAssessment.EvidenceItem> items) {
    var positive = new HashSet<String>();
    var negated = new HashSet<String>();
    for (var item : items) {
      if (item.matchedCriterion() == null) continue;
      String key = StudyPreparation.base(item.matchedCriterion());
      switch (item.mentionKind()) {
        case required, optional, usage -> positive.add(key);
        case negated -> negated.add(key);
        default -> {}
      }
    }
    positive.removeAll(negated);
    return positive;
  }

  private static String level(String seniority) {
    return switch (normalize(seniority)) {
      case "junior", "jr" -> "junior";
      case "pleno", "pl", "mid" -> "pleno";
      case "senior", "sr" -> "senior";
      default -> null;
    };
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
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
