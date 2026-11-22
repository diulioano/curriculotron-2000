package dev.curriculotron.opportunities.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decide a exceção júnior (US006) para quem não busca Júnior: trabalho aderente, salário anunciado
 * confirmado no piso próprio do regime e justificativa aceita da empresa. A mesma decisão alimenta
 * o ranking e o detalhe da vaga, para que a explicação não dependa da redação dos motivos.
 */
public final class JuniorExceptionPolicy {
  private JuniorExceptionPolicy() {}

  public enum Outcome {
    qualified,
    excluded
  }

  public enum SalaryStatus {
    /** Mínimo mensal anunciado atinge o piso. */
    confirmed,
    /** Faixa anunciada alcança o piso, mas o mínimo não o garante. */
    range_crosses_floor,
    below_floor,
    /** Sem valor mensal anunciado; estimativas não confirmam o piso. */
    not_announced,
    /** Regime diferente de CLT/PJ: não há piso aplicável. */
    contract_unknown,
    /** Perfil sem piso júnior para o regime da vaga. */
    floor_missing
  }

  public enum JustificationStatus {
    accepted,
    /** Empresa sem justificativa registrada. */
    missing,
    /** Justificativa registrada, mas os critérios não aceitam júnior. */
    not_accepted_by_criteria
  }

  /** Salário anunciado comparado com o piso do perfil, sem conversões. */
  public record SalaryCheck(
      SalaryStatus status,
      String contract,
      String unit,
      BigDecimal floor,
      BigDecimal announcedMin,
      BigDecimal announcedMax) {
    public SalaryCheck {
      Objects.requireNonNull(status);
    }

    public boolean confirmed() {
      return status == SalaryStatus.confirmed;
    }
  }

  /** {@code text} preserva a razão da empresa mesmo quando outra condição falha. */
  public record Justification(JustificationStatus status, String text) {
    public Justification {
      Objects.requireNonNull(status);
    }

    public boolean accepted() {
      return status == JustificationStatus.accepted;
    }
  }

  public record Decision(
      Outcome outcome, boolean workFit, SalaryCheck salary, Justification justification) {
    public Decision {
      Objects.requireNonNull(outcome);
      Objects.requireNonNull(salary);
      Objects.requireNonNull(justification);
    }

    public boolean qualified() {
      return outcome == Outcome.qualified;
    }

    /** Condições não atendidas, na redação usada pelos motivos do ranking. */
    public List<String> missing() {
      var missing = new ArrayList<String>();
      if (!workFit) missing.add("fit de cargo e stack");
      if (!salary.confirmed()) missing.add("salário anunciado confirmado acima do piso júnior");
      if (!justification.accepted()) missing.add("justificativa júnior da empresa");
      return missing;
    }
  }

  public static Decision decide(
      boolean workFit,
      RuleBasedRanker.Profile profile,
      RuleBasedRanker.AnnouncedSalary announced,
      boolean acceptJunior,
      String justification) {
    var salary = salary(profile, announced);
    var reason = justification(acceptJunior, justification);
    boolean qualified = workFit && salary.confirmed() && reason.accepted();
    return new Decision(qualified ? Outcome.qualified : Outcome.excluded, workFit, salary, reason);
  }

  static SalaryCheck salary(
      RuleBasedRanker.Profile profile, RuleBasedRanker.AnnouncedSalary announced) {
    String contract = announced == null ? null : announced.contract();
    String unit = announced == null ? null : announced.unit();
    BigDecimal min = announced == null ? null : announced.min();
    BigDecimal max = announced == null ? null : announced.max();
    BigDecimal floor =
        switch (contract == null ? "" : contract) {
          case "clt" -> profile.juniorFloorClt();
          case "pj" -> profile.juniorFloorPj();
          default -> null;
        };
    SalaryStatus status;
    if (!"clt".equals(contract) && !"pj".equals(contract)) status = SalaryStatus.contract_unknown;
    else if (floor == null) status = SalaryStatus.floor_missing;
    else if (!"monthly".equals(unit) || (min == null && max == null))
      status = SalaryStatus.not_announced;
    else if (min != null && min.compareTo(floor) >= 0) status = SalaryStatus.confirmed;
    else if ((max != null ? max : min).compareTo(floor) >= 0)
      status = SalaryStatus.range_crosses_floor;
    else status = SalaryStatus.below_floor;
    return new SalaryCheck(status, contract, unit, floor, min, max);
  }

  private static Justification justification(boolean acceptJunior, String text) {
    if (text == null || text.isBlank()) return new Justification(JustificationStatus.missing, null);
    return new Justification(
        acceptJunior ? JustificationStatus.accepted : JustificationStatus.not_accepted_by_criteria,
        text);
  }
}
