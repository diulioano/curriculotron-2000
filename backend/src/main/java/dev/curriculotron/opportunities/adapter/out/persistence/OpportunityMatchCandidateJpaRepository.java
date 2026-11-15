package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunityMatchCandidateJpaRepository
    extends JpaRepository<OpportunityMatchCandidateEntity, OpportunityMatchCandidateId> {
  List<OpportunityMatchCandidateEntity>
      findByStateOrderByCreatedAtAscOpportunityAAscOpportunityBAsc(String state);

  @Modifying
  @Query(
      "update OpportunityMatchCandidateEntity c set c.state = 'rejected', c.updatedAt = :at "
          + "where c.opportunityA = :a and c.opportunityB = :b and c.state = 'open'")
  int reject(@Param("a") String a, @Param("b") String b, @Param("at") String at);

  @Modifying
  @Query(
      "update OpportunityMatchCandidateEntity c set c.state = 'merged_by_user', c.updatedAt = :at "
          + "where c.opportunityA = :a and c.opportunityB = :b and c.state = 'open' "
          + "and not exists (select o.id from OpportunityEntity o where "
          + "(o.id = :a or o.id = :b) and (o.mergedInto is not null or o.userDecision = 'applied'))")
  int markMerged(@Param("a") String a, @Param("b") String b, @Param("at") String at);

  @Modifying
  @Query(
      "update OpportunityMatchCandidateEntity c set c.state = 'rejected', c.updatedAt = :at "
          + "where c.state = 'open' and (c.opportunityA = :merged or c.opportunityB = :merged)")
  int rejectOpenForMerged(@Param("merged") String merged, @Param("at") String at);
}
