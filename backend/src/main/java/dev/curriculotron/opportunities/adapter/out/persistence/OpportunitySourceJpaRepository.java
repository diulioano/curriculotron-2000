package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface OpportunitySourceJpaRepository extends JpaRepository<OpportunitySourceEntity, String> {
  Optional<OpportunitySourceEntity> findByCanonicalUrl(String canonicalUrl);

  Optional<OpportunitySourceEntity> findBySourceAndExternalId(String source, String externalId);

  List<OpportunitySourceEntity> findByOpportunityIdOrderByFetchedAtAscIdAsc(String opportunityId);

  boolean existsByCanonicalUrl(String canonicalUrl);

  boolean existsBySourceAndExternalId(String source, String externalId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "update OpportunitySourceEntity s set s.opportunityId = :survivor "
          + "where s.opportunityId = :merged")
  int moveToSurvivor(
      @org.springframework.data.repository.query.Param("survivor") String survivor,
      @org.springframework.data.repository.query.Param("merged") String merged);
}
