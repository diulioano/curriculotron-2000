package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class OpportunitySourcePersistenceAdapter implements OpportunitySourceRepository {
  private final OpportunitySourceJpaRepository sources;

  OpportunitySourcePersistenceAdapter(OpportunitySourceJpaRepository sources) {
    this.sources = sources;
  }

  @Override
  public List<Source> forOpportunity(String id) {
    return sources.findByOpportunityIdOrderByFetchedAtAscIdAsc(id).stream()
        .map(source -> new Source(source.source(), source.canonicalUrl(), source.fetchedAt()))
        .toList();
  }

  @Override
  public List<Discovery> discoveries(String id) {
    return sources.findByOpportunityIdOrderByCreatedAtAscIdAsc(id).stream()
        .map(source -> new Discovery(source.id(), source.source(), source.createdAt()))
        .toList();
  }
}
