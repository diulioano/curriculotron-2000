package dev.curriculotron.opportunities;

import static org.assertj.core.api.Assertions.*;

import dev.curriculotron.opportunities.domain.ObjectiveFilter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ObjectiveFilterTest {
  private final ObjectiveFilter filter = new ObjectiveFilter();

  @Test
  void excludesOnlyExplicitlyInsufficientMonthlySalary() {
    assertThat(evaluate("clt", "monthly", "5000", "5999").eligible()).isFalse();
    assertThat(evaluate("pj", "monthly", null, "8999").eligible()).isFalse();
    assertThat(evaluate("clt", "monthly", "6000", null).eligible()).isTrue();
    assertThat(evaluate("pj", "monthly", "9000", "12000").eligible()).isTrue();
  }

  @Test
  void missingAndCrossingRangesRequireConfirmation() {
    assertThat(evaluate("clt", "monthly", null, null).confirmationRequired()).isTrue();
    assertThat(evaluate("clt", "monthly", "5000", "7000").eligible()).isTrue();
    assertThat(evaluate("clt", "monthly", "5000", "7000").confirmationRequired()).isTrue();
    assertThat(evaluate("clt", "monthly", "6000", "7000").confirmationRequired()).isFalse();
    assertThat(evaluate("clt", "monthly", "5000", null).confirmationRequired()).isTrue();
  }

  @Test
  void neverConvertsAmbiguousUnitsOrUnknownContracts() {
    for (String unit : new String[] {"unknown", "hourly", "yearly"}) {
      var result = evaluate("clt", unit, "50", "100");
      assertThat(result.eligible()).isTrue();
      assertThat(result.confirmationRequired()).isTrue();
    }
    assertThat(evaluate("unknown", "monthly", "1000", "2000").confirmationRequired()).isTrue();
  }

  @Test
  void rejectsInvalidRangesAndThresholds() {
    assertThatThrownBy(() -> evaluate("clt", "monthly", "7000", "6000"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> evaluate("clt", "monthly", "-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ObjectiveFilter.Result evaluate(String contract, String unit, String min, String max) {
    return filter.evaluate(
        contract, unit, decimal(min), decimal(max), new BigDecimal("6000"), new BigDecimal("9000"));
  }

  private BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
