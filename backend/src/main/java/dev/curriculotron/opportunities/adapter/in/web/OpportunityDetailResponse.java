package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository.Source;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.SeniorityAssessment;
import dev.curriculotron.salary.application.SalaryReference;
import java.util.List;
import java.util.Objects;

public record OpportunityDetailResponse(
    String id,
    String title,
    String companyName,
    String location,
    String workMode,
    String contractType,
    Opportunity.Salary salary,
    List<String> fitReasons,
    String userDecision,
    String availability,
    String deadlineAt,
    String publishedAt,
    Source source,
    String companyTier,
    SalaryReference salaryReference,
    String glassdoorUrl,
    CompatibilitySummary compatibility,
    List<CompatibilityEvidenceView> compatibilityEvidence,
    CompatibilityCriteriaView compatibilityCriteria,
    List<String> facts,
    List<String> inferred,
    List<String> missing,
    List<Source> sources,
    List<CorrectableOpportunityField> correctedFields,
    List<OpportunityCorrectionView> corrections,
    SeniorityAssessment seniority,
    List<OpportunityTimelineView> timeline) {
  public record CompatibilityEvidenceView(
      String source,
      String sourceUrl,
      String fetchedAt,
      CompatibilityAssessment.Field field,
      String section,
      String quote,
      String matchedCriterion,
      String observedTerm,
      CompatibilityAssessment.MentionKind mentionKind,
      String observedVersion,
      String configuredVersion) {
    public CompatibilityEvidenceView {
      Objects.requireNonNull(field);
      Objects.requireNonNull(quote);
      Objects.requireNonNull(mentionKind);
    }
  }

  public record CompatibilityCriteriaView(List<String> targetRoles, List<String> stackKeywords) {
    public CompatibilityCriteriaView {
      targetRoles = List.copyOf(targetRoles);
      stackKeywords = List.copyOf(stackKeywords);
    }
  }

  public OpportunityDetailResponse {
    Objects.requireNonNull(compatibility);
    Objects.requireNonNull(seniority);
    timeline = List.copyOf(timeline);
    fitReasons = List.copyOf(fitReasons);
    compatibilityEvidence = List.copyOf(compatibilityEvidence);
    facts = List.copyOf(facts);
    inferred = List.copyOf(inferred);
    missing = List.copyOf(missing);
    sources = List.copyOf(sources);
    correctedFields = List.copyOf(correctedFields);
    corrections = List.copyOf(corrections);
  }
}
