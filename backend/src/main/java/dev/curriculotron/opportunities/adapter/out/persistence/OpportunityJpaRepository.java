package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunityJpaRepository extends JpaRepository<OpportunityEntity, String> {
  List<OpportunityEntity> findByMergedIntoIsNullOrderByIdAsc();

  List<OpportunityEntity> findByMergedIntoIsNullAndUserDecisionOrderByUpdatedAtDescIdAsc(
      String userDecision);

  @Modifying
  @Query(
      "update OpportunityEntity o set o.userDecision = :decision, o.updatedAt = :at, "
          + "o.decisionRevertedAt = case when :decision = 'none' then :at else o.decisionRevertedAt end "
          + "where o.id = :id and o.mergedInto is null and o.userDecision <> 'applied'")
  int updateDecision(
      @Param("id") String id, @Param("decision") String decision, @Param("at") String at);

  @Query(
      "select b.id from OpportunityEntity a, OpportunityEntity b "
          + "where a.id = :id and b.id <> a.id and a.mergedInto is null and b.mergedInto is null "
          + "and lower(trim(a.title)) = lower(trim(b.title)) "
          + "and lower(trim(a.companyName)) = lower(trim(b.companyName)) "
          + "and lower(trim(coalesce(a.location, ''))) = lower(trim(coalesce(b.location, '')))")
  List<String> findSimilar(@Param("id") String id);
}
