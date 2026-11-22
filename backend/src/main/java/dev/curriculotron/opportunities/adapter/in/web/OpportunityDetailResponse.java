package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository.Source;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.JuniorExceptionPolicy;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.SeniorityAssessment;
import dev.curriculotron.opportunities.domain.StudyPreparation;
import dev.curriculotron.salary.application.SalaryReference;
import java.math.BigDecimal;
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
    List<OpportunityTimelineView> timeline,
    StudyPreparation.Plan preparation,
    JuniorExceptionView juniorException) {
  /**
   * Exceção júnior (US006) calculada com os dados atuais e o mesmo nível do ranking. {@code
   * not_applicable}: nível do ranking não é júnior ou júnior é nível alvo; {@code unknown}: sem
   * perfil salvo ou sem dados para avaliar. Os demais campos só vêm preenchidos em {@code
   * qualified}/{@code excluded}.
   */
  public record JuniorExceptionView(
      Status status, Boolean workFit, SalaryView salary, JustificationView justification) {
    public enum Status {
      qualified,
      excluded,
      not_applicable,
      unknown
    }

    /** {@code source}: {@code announced}, {@code estimate} (só estimativa) ou {@code none}. */
    public record SalaryView(
        JuniorExceptionPolicy.SalaryStatus status,
        String source,
        String contract,
        String unit,
        BigDecimal floor,
        BigDecimal announcedMin,
        BigDecimal announcedMax) {}

    public record JustificationView(
        JuniorExceptionPolicy.JustificationStatus status, String text) {}

    public static final JuniorExceptionView NOT_APPLICABLE =
        new JuniorExceptionView(Status.not_applicable, null, null, null);
    public static final JuniorExceptionView UNKNOWN =
        new JuniorExceptionView(Status.unknown, null, null, null);

    public JuniorExceptionView {
      Objects.requireNonNull(status);
    }

    public static JuniorExceptionView of(
        JuniorExceptionPolicy.Decision decision, boolean estimateAvailable) {
      var salary = decision.salary();
      boolean announced =
          "monthly".equals(salary.unit())
              && (salary.announcedMin() != null || salary.announcedMax() != null);
      return new JuniorExceptionView(
          decision.qualified() ? Status.qualified : Status.excluded,
          decision.workFit(),
          new SalaryView(
              salary.status(),
              announced ? "announced" : estimateAvailable ? "estimate" : "none",
              salary.contract(),
              salary.unit(),
              salary.floor(),
              salary.announcedMin(),
              salary.announcedMax()),
          new JustificationView(
              decision.justification().status(), decision.justification().text()));
    }
  }

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
    Objects.requireNonNull(preparation);
    Objects.requireNonNull(juniorException);
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
