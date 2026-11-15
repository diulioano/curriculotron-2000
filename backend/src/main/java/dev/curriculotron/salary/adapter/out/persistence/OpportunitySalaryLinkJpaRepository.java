package dev.curriculotron.salary.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunitySalaryLinkJpaRepository
    extends JpaRepository<OpportunitySalaryLinkEntity, String> {
  List<OpportunitySalaryLinkEntity> findByOpportunityIdAndSupersededAtIsNull(String opportunityId);

  @Modifying
  @Query(
      "update OpportunitySalaryLinkEntity link set link.supersededAt = :at "
          + "where link.opportunityId = :opportunityId and link.supersededAt is null")
  int supersedeCurrent(@Param("opportunityId") String opportunityId, @Param("at") String at);
}
