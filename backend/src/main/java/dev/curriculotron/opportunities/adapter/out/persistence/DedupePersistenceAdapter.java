package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.DedupeRepository;
import dev.curriculotron.shared.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class DedupePersistenceAdapter implements DedupeRepository {
  private final ExternalIdentityJpaRepository externalIdentities;
  private final UrlIdentityJpaRepository urlIdentities;
  private final OpportunitySourceJpaRepository sources;
  private final OpportunityJpaRepository opportunities;
  private final OpportunityMatchCandidateJpaRepository candidates;

  DedupePersistenceAdapter(
      ExternalIdentityJpaRepository externalIdentities,
      UrlIdentityJpaRepository urlIdentities,
      OpportunitySourceJpaRepository sources,
      OpportunityJpaRepository opportunities,
      OpportunityMatchCandidateJpaRepository candidates) {
    this.externalIdentities = externalIdentities;
    this.urlIdentities = urlIdentities;
    this.sources = sources;
    this.opportunities = opportunities;
    this.candidates = candidates;
  }

  @Override
  public Optional<String> byExternalId(String source, String externalId) {
    if (externalId == null) return Optional.empty();
    return externalIdentities
        .findById(new ExternalIdentityId(source, externalId))
        .map(ExternalIdentityEntity::opportunityId);
  }

  @Override
  public Optional<String> byUrl(String url) {
    return urlIdentities.findById(url).map(UrlIdentityEntity::opportunityId);
  }

  @Override
  public void attach(
      String id, String source, String externalId, String url, String excerpt, Instant at) {
    if (!urlIdentities.existsById(url)) {
      urlIdentities.save(new UrlIdentityEntity(url, id));
    }
    if (externalId != null
        && !externalIdentities.existsById(new ExternalIdentityId(source, externalId))) {
      externalIdentities.save(new ExternalIdentityEntity(source, externalId, id));
    }

    boolean sourceAlreadyStored =
        sources.existsByCanonicalUrl(url)
            || (externalId != null && sources.existsBySourceAndExternalId(source, externalId));
    if (!sourceAlreadyStored) {
      String instant = at.toString();
      sources.save(
          new OpportunitySourceEntity(Ids.newId(), id, source, externalId, url, instant, excerpt));
    }
  }

  @Override
  public List<String> similar(String id) {
    return opportunities.findSimilar(id);
  }

  @Override
  public void suggest(String a, String b, Instant at) {
    String first = a.compareTo(b) < 0 ? a : b;
    String second = a.compareTo(b) < 0 ? b : a;
    var id = new OpportunityMatchCandidateId(first, second);
    if (!candidates.existsById(id)) {
      candidates.save(
          new OpportunityMatchCandidateEntity(
              first,
              second,
              "Identidades conflitantes ou título e empresa coincidentes",
              at.toString()));
    }
  }
}
