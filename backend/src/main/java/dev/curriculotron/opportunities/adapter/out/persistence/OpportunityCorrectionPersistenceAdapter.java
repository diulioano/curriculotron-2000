package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.opportunities.domain.OpportunityCorrectionEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class OpportunityCorrectionPersistenceAdapter implements OpportunityCorrectionRepository {
  private final OpportunityJpaRepository opportunities;
  private final OpportunityDatesJpaRepository dates;
  private final OpportunityCorrectionJpaRepository corrections;

  OpportunityCorrectionPersistenceAdapter(
      OpportunityJpaRepository opportunities,
      OpportunityDatesJpaRepository dates,
      OpportunityCorrectionJpaRepository corrections) {
    this.opportunities = opportunities;
    this.dates = dates;
    this.corrections = corrections;
  }

  @Override
  public Optional<CorrectableState> currentState(String opportunityId) {
    return opportunities
        .findById(opportunityId)
        .map(entity -> {
          var when = dates.findById(opportunityId).orElseThrow();
          return entity.correctableState(when.publishedAt(), when.deadlineAt());
        });
  }

  @Override
  public boolean apply(String opportunityId, CorrectedValues values, Instant at) {
    var entity = opportunities.findById(opportunityId).orElseThrow();
    boolean affectsAssessment = entity.applyCorrection(values, at.toString());
    opportunities.saveAndFlush(entity);
    dates.updateDates(opportunityId, values.publishedAt(), values.deadlineAt());
    return affectsAssessment;
  }

  @Override
  public Optional<String> deadlineAt(String opportunityId) {
    return dates.findById(opportunityId).map(OpportunityDatesEntity::deadlineAt);
  }

  @Override
  public Map<String, String> deadlinesAt(Collection<String> opportunityIds) {
    if (opportunityIds.isEmpty()) return Map.of();
    return dates.findAllById(opportunityIds).stream()
        .filter(entity -> entity.deadlineAt() != null)
        .collect(
            Collectors.toUnmodifiableMap(
                OpportunityDatesEntity::id, OpportunityDatesEntity::deadlineAt));
  }

  @Override
  public void append(List<OpportunityCorrectionEntry> entries) {
    corrections.saveAll(entries.stream().map(OpportunityCorrectionEntity::from).toList());
    corrections.flush();
  }

  @Override
  public Set<CorrectableOpportunityField> correctedFields(String opportunityId) {
    return Set.copyOf(corrections.distinctFieldNames(opportunityId));
  }

  @Override
  public Map<String, Set<CorrectableOpportunityField>> correctedFieldsOf(
      Collection<String> opportunityIds) {
    if (opportunityIds.isEmpty()) return Map.of();
    var byOpportunity = new HashMap<String, Set<CorrectableOpportunityField>>();
    for (var row : corrections.distinctFieldNamesByOpportunity(opportunityIds)) {
      byOpportunity
          .computeIfAbsent(
              row.opportunityId(), id -> EnumSet.noneOf(CorrectableOpportunityField.class))
          .add(row.fieldName());
    }
    return Map.copyOf(byOpportunity);
  }

  @Override
  public Optional<OpportunityCorrectionEntry> latestCorrection(
      String opportunityId, CorrectableOpportunityField field) {
    return corrections
        .findFirstByOpportunityIdAndFieldNameOrderByCorrectedAtDescIdDesc(opportunityId, field)
        .map(OpportunityCorrectionEntity::toDomain);
  }

  @Override
  public List<OpportunityCorrectionEntry> forOpportunity(String opportunityId) {
    return corrections.findByOpportunityIdOrderByCorrectedAtDescIdDesc(opportunityId).stream()
        .map(OpportunityCorrectionEntity::toDomain)
        .toList();
  }
}
