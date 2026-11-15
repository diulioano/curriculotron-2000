package dev.curriculotron.salary.domain;

import java.math.BigDecimal;
import java.time.*;
import java.util.Set;

public record SalaryEstimate(
    String id,
    SalaryProfile criteria,
    BigDecimal originalMin,
    BigDecimal originalMax,
    String originalUnit,
    String currency,
    Integer sampleSize,
    String source,
    String sourceUrl,
    Instant consultedAt,
    String sourceUpdatedOn,
    String evidence) {
  public SalaryEstimate {
    if (criteria == null
        || criteria.role().isBlank()
        || criteria.seniority().isBlank()
        || criteria.location().isBlank())
      throw new IllegalArgumentException("Critérios obrigatórios.");
    if (!Set.of("clt", "pj", "unknown").contains(criteria.contractType()))
      throw new IllegalArgumentException("Regime inválido.");
    SalaryNormalizer.range(originalMin, originalMax);
    SalaryNormalizer.monthly(originalMin, originalUnit);
    if (!"BRL".equals(currency)
        || sampleSize != null && sampleSize < 0
        || !"Glassdoor".equals(source)
        || evidence == null
        || evidence.isBlank()
        || evidence.length() > 2000
        || consultedAt == null
        || consultedAt.isAfter(Instant.now().plusSeconds(300)))
      throw new IllegalArgumentException("Proveniência inválida.");
    if (sourceUpdatedOn != null) LocalDate.parse(sourceUpdatedOn);
    sourceUrl = GlassdoorLink.validate(sourceUrl);
  }

  public Instant expiresAt() {
    return consultedAt.plus(Duration.ofDays(90));
  }

  public boolean stale(Instant now) {
    return !now.isBefore(expiresAt());
  }
}
