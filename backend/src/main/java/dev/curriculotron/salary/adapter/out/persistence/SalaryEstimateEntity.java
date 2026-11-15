package dev.curriculotron.salary.adapter.out.persistence;

import dev.curriculotron.salary.domain.SalaryEstimate;
import dev.curriculotron.salary.domain.SalaryProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "salary_estimate")
class SalaryEstimateEntity {
  @Id private String id;

  @Column(name = "profile_key", nullable = false)
  private String profileKey;

  @Column(nullable = false)
  private String company;

  @Column(nullable = false)
  private String role;

  @Column(nullable = false)
  private String seniority;

  @Column(nullable = false)
  private String location;

  @Column(name = "work_mode", nullable = false)
  private String workMode;

  @Column(name = "contract_type", nullable = false)
  private String contractType;

  @Column(name = "original_min", nullable = false)
  private BigDecimal originalMin;

  @Column(name = "original_max", nullable = false)
  private BigDecimal originalMax;

  @Column(name = "original_unit", nullable = false)
  private String originalUnit;

  @Column(name = "sample_size")
  private Integer sampleSize;

  @Column(nullable = false)
  private String source;

  @Column(name = "source_url", nullable = false)
  private String sourceUrl;

  @Column(name = "consulted_at", nullable = false)
  private String consultedAt;

  @Column(name = "source_updated_on")
  private String sourceUpdatedOn;

  @Column(nullable = false)
  private String evidence;

  protected SalaryEstimateEntity() {}

  SalaryEstimateEntity(SalaryEstimate estimate) {
    var profile = estimate.criteria();
    id = estimate.id();
    profileKey = profile.key();
    company = profile.company();
    role = profile.role();
    seniority = profile.seniority();
    location = profile.location();
    workMode = profile.workMode();
    contractType = profile.contractType();
    originalMin = estimate.originalMin();
    originalMax = estimate.originalMax();
    originalUnit = estimate.originalUnit();
    sampleSize = estimate.sampleSize();
    source = estimate.source();
    sourceUrl = estimate.sourceUrl();
    consultedAt = estimate.consultedAt().toString();
    sourceUpdatedOn = estimate.sourceUpdatedOn();
    evidence = estimate.evidence();
  }

  SalaryEstimate toDomain() {
    return new SalaryEstimate(
        id,
        new SalaryProfile(company, role, seniority, location, workMode, contractType),
        originalMin,
        originalMax,
        originalUnit,
        "BRL",
        sampleSize,
        source,
        sourceUrl,
        Instant.parse(consultedAt),
        sourceUpdatedOn,
        evidence);
  }

  Instant consultedInstant() {
    return Instant.parse(consultedAt);
  }

  String id() {
    return id;
  }
}
