package dev.curriculotron.opportunities.domain;

import java.math.BigDecimal;
import java.util.Set;

/** Conservative salary eligibility: uncertainty remains visible instead of becoming an estimate. */
public final class ObjectiveFilter {
  public record Result(boolean eligible, boolean confirmationRequired, String reason) {}

  public Result evaluate(
      String contract,
      String unit,
      BigDecimal min,
      BigDecimal max,
      BigDecimal cltMinimum,
      BigDecimal pjMinimum) {
    if (contract == null
        || !Set.of("clt", "pj", "other", "unknown").contains(contract)
        || unit == null
        || !Set.of("monthly", "hourly", "yearly", "unknown").contains(unit)
        || cltMinimum == null
        || cltMinimum.signum() <= 0
        || pjMinimum == null
        || pjMinimum.signum() <= 0
        || (min != null && min.signum() < 0)
        || (max != null && max.signum() < 0)
        || (min != null && max != null && min.compareTo(max) > 0)) {
      throw new IllegalArgumentException("Dados salariais inválidos.");
    }
    if (min == null && max == null) {
      return new Result(true, true, "Salário a confirmar.");
    }
    if (!unit.equals("monthly") || !(contract.equals("clt") || contract.equals("pj"))) {
      return new Result(
          true, true, "Confirmar contratação e remuneração mensal; valor não convertido.");
    }
    BigDecimal threshold = contract.equals("clt") ? cltMinimum : pjMinimum;
    if (max != null && max.compareTo(threshold) < 0) {
      return new Result(false, false, "Remuneração explicitamente abaixo do mínimo mensal.");
    }
    if (min == null || min.compareTo(threshold) < 0) {
      return new Result(true, true, "Confirmar oferta compatível com o mínimo mensal.");
    }
    return new Result(true, false, "Remuneração compatível com o mínimo mensal.");
  }
}
