package dev.curriculotron.applications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunityApplicationJpaRepository extends JpaRepository<StoredOpportunity, String> {
  @Modifying
  @Query(
      "update StoredOpportunity o set o.userDecision = 'applied', o.updatedAt = :at "
          + "where o.id = :id")
  int markApplied(@Param("id") String id, @Param("at") String at);
}
