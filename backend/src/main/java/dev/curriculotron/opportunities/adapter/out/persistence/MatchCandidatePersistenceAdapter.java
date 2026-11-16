package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MatchCandidatePersistenceAdapter implements MatchCandidateRepository {
  private final OpportunityMatchCandidateJpaRepository candidates;
  private final OpportunityJpaRepository opportunities;
  private final OpportunitySourceJpaRepository sources;
  private final UrlIdentityJpaRepository urlIdentities;
  private final ExternalIdentityJpaRepository externalIdentities;
  private final EvidenceJpaRepository evidence;

  MatchCandidatePersistenceAdapter(
      OpportunityMatchCandidateJpaRepository candidates,
      OpportunityJpaRepository opportunities,
      OpportunitySourceJpaRepository sources,
      UrlIdentityJpaRepository urlIdentities,
      ExternalIdentityJpaRepository externalIdentities,
      EvidenceJpaRepository evidence) {
    this.candidates = candidates;
    this.opportunities = opportunities;
    this.sources = sources;
    this.urlIdentities = urlIdentities;
    this.externalIdentities = externalIdentities;
    this.evidence = evidence;
  }

  @Override
  public List<Candidate> open() {
    return candidates.findByStateOrderByCreatedAtAscOpportunityAAscOpportunityBAsc("open").stream()
        .map(candidate ->
            new Candidate(
                candidate.opportunityA() + ":" + candidate.opportunityB(),
                candidate.opportunityA(),
                candidate.opportunityB(),
                candidate.signal()))
        .toList();
  }

  @Override
  public boolean reject(String a, String b, String at) {
    return candidates.reject(a, b, at) == 1;
  }

  @Override
  public boolean merge(String a, String b, String at) {
    if (candidates.markMerged(a, b, at) == 0) return false;

    var survivor = opportunities.findById(a).orElseThrow();
    var merged = opportunities.findById(b).orElseThrow();
    if ("discarded".equals(merged.userDecision()) || "discarded".equals(survivor.userDecision())) {
      survivor.setDecision("discarded", at);
    } else {
      survivor.setDecision(survivor.userDecision(), at);
    }
    survivor.invalidateEvidence();
    merged.mergeInto(a, at);
    opportunities.saveAllAndFlush(List.of(survivor, merged));

    sources.moveToSurvivor(a, b);
    urlIdentities.moveToSurvivor(a, b);
    externalIdentities.moveToSurvivor(a, b);
    evidence.moveToSurvivor(a, b);
    candidates.rejectOpenForMerged(b, at);
    return true;
  }
}
