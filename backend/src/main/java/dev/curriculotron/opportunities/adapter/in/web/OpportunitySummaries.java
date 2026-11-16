package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.CompatibilityRepository;
import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.salary.application.SalaryEstimateService;
import dev.curriculotron.sources.PublicationStore;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.util.List;
import java.util.Optional;
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

  public OpportunitySummaries(
      OpportunitySourceRepository sources,
      SalaryEstimateService salary,
      PublicationStore publications,
      CompatibilityRepository assessments,
      EvidenceRepository evidence,
      SearchCriteriaRepository criteria) {
    this.sources = sources;
    this.salary = salary;
    this.publications = publications;
    this.assessments = assessments;
    this.evidence = evidence;
    this.criteria = criteria;
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
    return items.stream().map(item -> of(item, criteriaVersion)).toList();
  }

  @Transactional(readOnly = true)
  public OpportunitySummary of(Opportunity item) {
    return of(item, criteria.current().version());
  }

  @Transactional(readOnly = true)
  public DetailProjection detailOf(Opportunity item) {
    long criteriaVersion = criteria.current().version();
    Optional<CompatibilityAssessment> assessment = assessments.find(item.id());
    List<OpportunitySourceRepository.Source> found = sources.forOpportunity(item.id());
    CompatibilitySummary compatibility = compatibility(item.id(), criteriaVersion, assessment);
    OpportunitySummary summary = summaryFor(item, found, compatibility);
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

  private OpportunitySummary of(Opportunity item, long criteriaVersion) {
    List<OpportunitySourceRepository.Source> found = sources.forOpportunity(item.id());
    CompatibilitySummary compatibility =
        compatibility(item.id(), criteriaVersion, assessments.find(item.id()));
    return summaryFor(item, found, compatibility);
  }

  private OpportunitySummary summaryFor(
      Opportunity item,
      List<OpportunitySourceRepository.Source> found,
      CompatibilitySummary compatibility) {
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
        null,
        publications.find(item.id()),
        found.isEmpty() ? null : found.getLast(),
        null,
        salary.reference(item),
        salary.glassdoorUrl(item),
        compatibility);
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
