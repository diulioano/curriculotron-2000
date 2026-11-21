package dev.curriculotron.opportunities.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunityDatesJpaRepository extends JpaRepository<OpportunityDatesEntity, String> {
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update OpportunityDatesEntity o set o.publishedAt = :publishedAt, "
          + "o.deadlineAt = :deadlineAt where o.id = :id")
  int updateDates(
      @Param("id") String id,
      @Param("publishedAt") String publishedAt,
      @Param("deadlineAt") String deadlineAt);
}
