package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.sources.PublicationStore;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Maps the opportunity domain model to the stable list DTO consumed by the panel. */
@Component
public class OpportunitySummaries {
  private final OpportunitySourceRepository sources;
  private final SalaryEstimateService salary;
  private final PublicationStore publications;
  private final CompatibilityRepository assessments;
  private final EvidenceRepository evidence;
  private final SearchCriteriaRepository criteria;
  private final OpportunityCorrectionRepository corrections;

  public OpportunitySummaries(
      OpportunitySourceRepository sources,
      SalaryEstimateService salary,
      PublicationStore publications,
      CompatibilityRepository assessments,
      EvidenceRepository evidence,
      SearchCriteriaRepository criteria,
      OpportunityCorrectionRepository corrections) {
    this.sources = sources;
    this.salary = salary;
    this.publications = publications;
    this.assessments = assessments;
    this.evidence = evidence;
    this.criteria = criteria;
    this.corrections = corrections;
  }

  public record DetailProjection(
      OpportunitySummary summary,
      List<OpportunityDetailResponse.CompatibilityEvidenceView> compatibilityEvidence,
      OpportunityDetailResponse.CompatibilityCriteriaView compatibilityCriteria,
      List<VacancyEvidence> storedEvidence,
      List<OpportunitySourceRepository.Source> sources) {}

  @Transactional(readOnly = true)
  public List<OpportunitySummary> of(List<Opportunity> items) {
    long criteriaVersion = criteria.current().version();
    var ids = items.stream().map(Opportunity::id).toList();
    Map<String, String> deadlines = corrections.deadlinesAt(ids);
    Map<String, Set<CorrectableOpportunityField>> corrected = corrections.correctedFieldsOf(ids);
    return items.stream()
        .map(item -> of(item, criteriaVersion, deadlines.get(item.id()), corrected.get(item.id())))
        .toList();
  }

  @Transactional(readOnly = true)
  public OpportunitySummary of(Opportunity item) {
    return of(
        item,
        criteria.current().version(),
        corrections.deadlineAt(item.id()).orElse(null),
        corrections.correctedFields(item.id()));
  }

  @Transactional(readOnly = true)
  public DetailProjection detailOf(Opportunity item) {
    long criteriaVersion = criteria.current().version();
    Optional<CompatibilityAssessment> assessment = assessments.find(item.id());
    List<OpportunitySourceRepository.Source> found = sources.forOpportunity(item.id());
    CompatibilitySummary compatibility = compatibility(item.id(), criteriaVersion, assessment);
    OpportunitySummary summary =
        summaryFor(
            item,
            found,
            compatibility,
            corrections.deadlineAt(item.id()).orElse(null),
            corrections.correctedFields(item.id()));
    List<OpportunityDetailResponse.CompatibilityEvidenceView> evidenceViews =
        assessment
            .map(a ->
                a.evidenceItems().stream()
                    .map(ev ->
                        new OpportunityDetailResponse.CompatibilityEvidenceView(
                            ev.source(),
                            ev.sourceUrl(),
                            ev.fetchedAt() == null ? null : ev.fetchedAt().toString(),
                            ev.field(),
                            ev.section(),
                            ev.quote(),
                            ev.matchedCriterion(),
                            ev.observedTerm(),
                            ev.mentionKind(),
                            ev.observedVersion(),
                            ev.configuredVersion()))
                    .toList())
            .orElseGet(List::of);
    OpportunityDetailResponse.CompatibilityCriteriaView criteriaView =
        assessment
            .map(a ->
                new OpportunityDetailResponse.CompatibilityCriteriaView(
                    a.criteriaSnapshot().criteria().roles().stream().sorted().toList(),
                    a.criteriaSnapshot().criteria().stack().stream().sorted().toList()))
            .orElse(null);
    return new DetailProjection(
        summary, evidenceViews, criteriaView, evidence.forOpportunity(item.id()), found);
  }

  private OpportunitySummary of(
      Opportunity item,
      long criteriaVersion,
      String deadlineAt,
      Set<CorrectableOpportunityField> correctedFields) {
    List<OpportunitySourceRepository.Source> found = sources.forOpportunity(item.id());
    CompatibilitySummary compatibility =
        compatibility(item.id(), criteriaVersion, assessments.find(item.id()));
    return summaryFor(item, found, compatibility, deadlineAt, correctedFields);
  }

  private OpportunitySummary summaryFor(
      Opportunity item,
      List<OpportunitySourceRepository.Source> found,
      CompatibilitySummary compatibility,
      String deadlineAt,
      Set<CorrectableOpportunityField> correctedFields) {
    List<String> fitReasons = compatibility.current() ? item.fitReasons() : List.of();
    return new OpportunitySummary(
        item.id(),
        item.title(),
        item.companyName(),
        item.location(),
        item.workMode(),
        item.contractType(),
        item.salary(),
        fitReasons,
        item.userDecision(),
        item.availability(),
        deadlineAt,
        publications.find(item.id()),
        found.isEmpty() ? null : found.getLast(),
        null,
        salary.reference(item),
        salary.glassdoorUrl(item),
        compatibility,
        inFieldOrder(correctedFields));
  }

  /** Ordem estável do enum, independente da ordem de gravação das correções. */
  private static List<CorrectableOpportunityField> inFieldOrder(
      Set<CorrectableOpportunityField> fields) {
    if (fields == null || fields.isEmpty()) return List.of();
    return Arrays.stream(CorrectableOpportunityField.values()).filter(fields::contains).toList();
  }

  private CompatibilitySummary compatibility(
      String opportunityId, long criteriaVersion, Optional<CompatibilityAssessment> assessment) {
    return assessment
        .map(found ->
            new CompatibilitySummary(
                found.criteriaVersion(),
                found.evaluatorVersion(),
                found.assessedAt().toString(),
                found.current(criteriaVersion, evidence.revision(opportunityId)),
                found.roleStatus(),
                found.technologyStatus(),
                found.reasonCodes()))
        .orElseGet(CompatibilitySummary::notAssessed);
  }
}
