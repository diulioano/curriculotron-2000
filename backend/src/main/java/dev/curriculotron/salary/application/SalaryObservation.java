package dev.curriculotron.salary.application;

import dev.curriculotron.salary.domain.SalaryProfile;
import java.math.BigDecimal;
import java.time.Instant;

public record SalaryObservation(
    String id,
    SalaryProfile criteria,
    String currency,
    BigDecimal originalMin,
    BigDecimal originalMax,
    String originalUnit,
    BigDecimal monthlyMin,
    BigDecimal monthlyMax,
    boolean convertedFromAnnual,
    Integer sampleSize,
    String source,
    String sourceUrl,
    Instant consultedAt,
    Instant expiresAt,
    boolean stale,
    String sourceUpdatedOn,
    String scope) {}
