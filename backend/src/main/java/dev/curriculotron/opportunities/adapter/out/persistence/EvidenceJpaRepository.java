package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EvidenceJpaRepository extends JpaRepository<OpportunityEvidenceEntity, String> {
  List<OpportunityEvidenceEntity> findByOpportunityIdOrderByIdAsc(String opportunityId);

  Optional<OpportunityEvidenceEntity> findBySourceId(String sourceId);

  @Query("select o.evidenceRevision from OpportunityEntity o where o.id=:id")
  Optional<Long> revision(@Param("id") String id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update OpportunityEvidenceEntity e set e.opportunityId=:survivor where e.opportunityId=:merged")
  int moveToSurvivor(@Param("survivor") String survivor, @Param("merged") String merged);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update OpportunityEntity o set o.evidenceRevision=o.evidenceRevision+1 where o.id=:id")
  int invalidate(@Param("id") String id);
}
