package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CompatibilityJpaRepository extends JpaRepository<OpportunityCompatibilityEntity, String> {
  String PENDING =
      " from OpportunityEntity o left join OpportunityCompatibilityEntity a on a.opportunityId=o.id "
          + "where o.mergedInto is null and (a.opportunityId is null or a.criteriaVersion<>:version "
          + "or a.evaluatorVersion<>:evaluator or a.evidenceRevision<>o.evidenceRevision) ";

  @Query("select o.id" + PENDING + "and o.id>:after order by o.id")
  List<String> pending(
      @Param("version") long version,
      @Param("evaluator") int evaluator,
      @Param("after") String after,
      Pageable page);

  @Query("select count(o)" + PENDING)
  long pendingCount(@Param("version") long version, @Param("evaluator") int evaluator);

  @Query(
      "select count(o) from OpportunityEntity o where o.id=:id and o.mergedInto is null and o.evidenceRevision=:revision "
          + "and exists(select c.id from SearchCriteriaEntity c where c.id=1 and c.version=:version)")
  long currentInput(
      @Param("id") String id, @Param("revision") long revision, @Param("version") long version);

  @Modifying
  @Query(
      "update OpportunityEntity o set o.fit=:fit,o.fitScore=:score,o.fitReasons=:reasons where o.id=:id")
  int updateFit(
      @Param("id") String id,
      @Param("fit") String fit,
      @Param("score") int score,
      @Param("reasons") List<String> reasons);
}
