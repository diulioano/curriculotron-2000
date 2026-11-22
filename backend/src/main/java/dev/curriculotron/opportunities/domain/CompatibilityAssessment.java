package dev.curriculotron.opportunities.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CompatibilityAssessment(
    String opportunityId,
    long criteriaVersion,
    int evaluatorVersion,
    long evidenceRevision,
    Instant assessedAt,
    Axis roleStatus,
    Axis technologyStatus,
    List<String> reasonCodes,
    CriteriaSnapshot criteriaSnapshot,
    List<EvidenceItem> evidenceItems,
    String fit,
    int fitScore,
    List<String> fitReasons) {
  public static final int EVALUATOR_VERSION = 2;

  public enum Axis {
    matched,
    mismatched,
    unknown,
    unrestricted
  }

  public enum MentionKind {
    required,
    optional,
    usage,
    negated,
    incidental,
    uncertain
  }

  public enum Field {
    title,
    description
  }

  public record CriteriaSnapshot(
      RuleBasedRanker.Criteria criteria, BigDecimal cltMinimum, BigDecimal pjMinimum) {
    public CriteriaSnapshot {
      Objects.requireNonNull(criteria);
      if (cltMinimum == null
          || pjMinimum == null
          || cltMinimum.signum() <= 0
          || pjMinimum.signum() <= 0)
        throw new IllegalArgumentException("Salary minima must be positive");
    }
  }

  public record EvidenceItem(
      String evidenceId,
      String source,
      String sourceUrl,
      Instant fetchedAt,
      Field field,
      String section,
      String quote,
      String matchedCriterion,
      String observedTerm,
      MentionKind mentionKind,
      String observedVersion,
      String configuredVersion) {
    public EvidenceItem {
      Objects.requireNonNull(field);
      Objects.requireNonNull(mentionKind);
      Objects.requireNonNull(quote);
      if (evidenceId == null || evidenceId.isBlank() || source == null || source.isBlank())
        throw new IllegalArgumentException("Evidence origin is required");
    }
  }

  public CompatibilityAssessment {
    if (opportunityId == null
        || opportunityId.isBlank()
        || criteriaVersion <= 0
        || evaluatorVersion <= 0
        || evidenceRevision <= 0)
      throw new IllegalArgumentException("Assessment identity and versions must be positive");
    Objects.requireNonNull(assessedAt);
    Objects.requireNonNull(roleStatus);
    Objects.requireNonNull(technologyStatus);
    Objects.requireNonNull(criteriaSnapshot);
    if (technologyStatus == Axis.unrestricted && !criteriaSnapshot.criteria().stack().isEmpty()) {
      throw new IllegalArgumentException("Unrestricted technology requires empty criteria");
    }
    if (roleStatus == Axis.unrestricted)
      throw new IllegalArgumentException("Role cannot be unrestricted");
    if (!Set.of("strong", "medium", "weak").contains(fit) || fitScore < 0)
      throw new IllegalArgumentException("Invalid assessment fit");
    reasonCodes = List.copyOf(reasonCodes);
    evidenceItems = List.copyOf(evidenceItems);
    fitReasons = List.copyOf(fitReasons);
  }

  public boolean compatible() {
    return roleStatus != Axis.mismatched && technologyStatus != Axis.mismatched;
  }

  public boolean confirmed() {
    return roleStatus == Axis.matched
        && (technologyStatus == Axis.matched || technologyStatus == Axis.unrestricted);
  }

  public boolean current(long version, long revision) {
    return criteriaVersion == version
        && evidenceRevision == revision
        && evaluatorVersion == EVALUATOR_VERSION;
  }
}
