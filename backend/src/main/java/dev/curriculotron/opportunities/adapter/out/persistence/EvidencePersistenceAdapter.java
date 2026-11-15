package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.EvidenceRepository;
import dev.curriculotron.opportunities.domain.VacancyEvidence;
import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
class EvidencePersistenceAdapter implements EvidenceRepository {
  private final EvidenceJpaRepository evidence;
  private final OpportunitySourceJpaRepository sources;

  EvidencePersistenceAdapter(
      EvidenceJpaRepository evidence, OpportunitySourceJpaRepository sources) {
    this.evidence = evidence;
    this.sources = sources;
  }

  public List<VacancyEvidence> forOpportunity(String id) {
    return evidence.findByOpportunityIdOrderByIdAsc(id).stream()
        .map(e ->
            new VacancyEvidence(
                e.id,
                e.opportunityId,
                e.sourceId,
                e.sourceKind,
                e.title,
                e.location,
                e.descriptionText,
                Completeness.valueOf(e.descriptionCompleteness),
                e.completenessReason,
                e.contentRevision,
                e.fetchedAt == null ? null : Instant.parse(e.fetchedAt),
                e.sourceId == null
                    ? null
                    : sources
                        .findById(e.sourceId)
                        .map(OpportunitySourceEntity::canonicalUrl)
                        .orElse(null)))
        .toList();
  }

  public long revision(String id) {
    return evidence
        .revision(id)
        .orElseThrow(() -> new IllegalArgumentException("Opportunity does not exist"));
  }

  public long upsert(VacancyEvidence value) {
    var existing =
        value.sourceId() == null
            ? evidence.findById(value.id())
            : evidence.findBySourceId(value.sourceId());
    var e = existing.orElseGet(OpportunityEvidenceEntity::new);
    if (existing.isPresent() && !e.opportunityId.equals(value.opportunityId()))
      throw new IllegalArgumentException("Evidence belongs to a different opportunity");
    boolean changed =
        existing.isEmpty()
            || !Objects.equals(e.title, value.title())
            || !Objects.equals(e.location, value.location())
            || !Objects.equals(e.descriptionText, value.text())
            || !Objects.equals(e.descriptionCompleteness, value.completeness().name())
            || !Objects.equals(e.completenessReason, value.completenessReason())
            || !Objects.equals(e.sourceKind, value.sourceKind());
    e.id = existing.isEmpty() ? value.id() : e.id;
    e.opportunityId = value.opportunityId();
    e.sourceId = value.sourceId();
    e.sourceKind = value.sourceKind();
    e.title = value.title();
    e.location = value.location();
    e.descriptionText = value.text();
    e.descriptionCompleteness = value.completeness().name();
    e.completenessReason = value.completenessReason();
    e.contentRevision = existing.isEmpty() ? 1 : e.contentRevision + (changed ? 1 : 0);
    e.fetchedAt = value.fetchedAt() == null ? null : value.fetchedAt().toString();
    evidence.saveAndFlush(e);
    if (changed) invalidate(value.opportunityId());
    return revision(value.opportunityId());
  }

  public void invalidate(String id) {
    if (evidence.invalidate(id) != 1)
      throw new IllegalArgumentException("Opportunity does not exist");
  }
}
