package dev.curriculotron.salary;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.salary.domain.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SalaryNormalizationTest {
  @Test
  void validatesCurrencyAndSampleWithoutLosingOriginalValues() {
    var p = new SalaryProfile("Company", "java", "pleno", "brasil", "", "clt");
    for (Integer sample : java.util.Arrays.asList(null, 0, 4, 5)) {
      var e = estimate(p, "BRL", sample);
      assertThat(e.sampleSize()).isEqualTo(sample);
      assertThat(e.originalMin()).isEqualTo(new BigDecimal("1.01"));
      assertThat(e.expiresAt()).isEqualTo(e.consultedAt().plus(java.time.Duration.ofDays(90)));
    }
    assertThatThrownBy(() -> estimate(p, "USD", 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> estimate(p, "BRL", -1)).isInstanceOf(IllegalArgumentException.class);
  }

  private SalaryEstimate estimate(SalaryProfile p, String currency, Integer sample) {
    return new SalaryEstimate(
        "test",
        p,
        new BigDecimal("1.01"),
        new BigDecimal("2.02"),
        "monthly",
        currency,
        sample,
        "Glassdoor",
        "https://www.glassdoor.com.br/",
        java.time.Instant.parse("2026-01-01T00:00:00Z"),
        null,
        "Faixa base 1.01 a 2.02 BRL");
  }

  @Test
  void preservesDecimalPrecisionAndRoundsAnnualHalfUp() {
    var monthly = new BigDecimal("6000.1234567890123456789");
    assertThat(SalaryNormalizer.monthly(monthly, "monthly")).isEqualTo(monthly);
    assertThat(SalaryNormalizer.monthly(new BigDecimal("0.06"), "yearly"))
        .isEqualByComparingTo("0.01");
    for (String unit : java.util.List.of("hourly", "daily", "unknown"))
      assertThat(SalaryNormalizer.monthly(monthly, unit)).isNull();
    SalaryNormalizer.range(BigDecimal.ZERO, BigDecimal.ZERO);
    assertThatThrownBy(() -> SalaryNormalizer.range(new BigDecimal("-0.01"), BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SalaryNormalizer.range(null, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SalaryNormalizer.range(BigDecimal.ONE, null))
        .isInstanceOf(IllegalArgumentException.class);
    for (String invalid : java.util.List.of("NaN", "Infinity", "-Infinity"))
      assertThatThrownBy(() -> new BigDecimal(invalid)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  void requiresRoleSeniorityLocationAndKnownRegime() {
    assertThat(new SalaryProfile("", "java", "pleno", "brasil", "", "pj").complete()).isTrue();
    for (var p :
        java.util.List.of(
            new SalaryProfile("a", "", "pleno", "brasil", "", "clt"),
            new SalaryProfile("a", "java", "", "brasil", "", "clt"),
            new SalaryProfile("a", "java", "pleno", "", "", "clt"),
            new SalaryProfile("a", "java", "pleno", "brasil", "", "unknown")))
      assertThat(p.complete()).isFalse();
  }

  @Test
  void annualAndNonMonthly() {
    assertThat(SalaryNormalizer.monthly(new BigDecimal("71000"), "yearly"))
        .isEqualByComparingTo("5916.67");
    assertThat(SalaryNormalizer.monthly(new BigDecimal("52"), "hourly")).isNull();
    assertThatThrownBy(() -> SalaryNormalizer.range(new BigDecimal("9"), new BigDecimal("4")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void profilesDoNotMixCompaniesOrRegimes() {
    var a =
        new SalaryProfile(
            "CWI Software", "Desenvolvedor Java", "pleno", "Brasil", "unknown", "clt");
    assertThat(a.key())
        .isEqualTo(
            new SalaryProfile(
                    " CWI  SOFTWARE ", "desenvolvedor java", "pleno", "Brasil", "unknown", "clt")
                .key());
    assertThat(a.key())
        .isNotEqualTo(
            new SalaryProfile(
                    "Stefanini", "Desenvolvedor Java", "pleno", "Brasil", "unknown", "clt")
                .key());
    assertThat(a.key())
        .isNotEqualTo(
            new SalaryProfile(
                    "CWI Software", "Desenvolvedor Java", "pleno", "Brasil", "unknown", "pj")
                .key());
    assertThat(
            new SalaryProfile(
                    "CWI Software", "Desenvolvedor Java", "pleno", "Brasil", "unknown", "unknown")
                .complete())
        .isFalse();
  }
}
