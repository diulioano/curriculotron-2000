package dev.curriculotron.salary.domain;

import java.math.*;
import java.util.Set;

public final class SalaryNormalizer {
  private SalaryNormalizer() {}

  public static void range(BigDecimal min, BigDecimal max) {
    if (min == null
        || max == null
        || min.signum() < 0
        || max.compareTo(min) < 0
        || max.compareTo(new BigDecimal("1000000000")) > 0)
      throw new IllegalArgumentException("Faixa salarial inválida.");
  }

  public static BigDecimal monthly(BigDecimal value, String unit) {
    if (!Set.of("monthly", "yearly", "hourly", "daily", "unknown").contains(unit))
      throw new IllegalArgumentException("Unidade inválida.");
    if (value == null) return null;
    return switch (unit) {
      case "monthly" -> value;
      case "yearly" -> value.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
      default -> null;
    };
  }
}
