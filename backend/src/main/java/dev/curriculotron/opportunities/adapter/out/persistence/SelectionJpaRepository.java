package dev.curriculotron.opportunities.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SelectionJpaRepository extends JpaRepository<DailySelectionEntity, DailySelectionId> {
  @Query(
      "select d.id.opportunityId from DailySelectionEntity d "
          + "where d.id.localDate = :date order by d.rank")
  List<String> selected(@Param("date") String date);

  @Query(
      "select o.id from OpportunityEntity o "
          + "where o.userDecision = 'none' and o.availability <> 'closed' and o.mergedInto is null "
          + "and not exists (select d.id from DailySelectionEntity d "
          + "join OpportunityEntity merged on merged.id = d.id.opportunityId where merged.mergedInto = o.id) "
          + "and not exists (select d.id from DailySelectionEntity d where d.id.opportunityId = o.id) "
          + "and o.fit = 'strong' order by o.fitScore desc, o.createdAt, o.id")
  List<String> eligible();

  @Query(
      "select o.id from OpportunityEntity o "
          + "where o.userDecision = 'none' and o.availability <> 'closed' and o.mergedInto is null "
          + "and not exists (select d.id from DailySelectionEntity d "
          + "join OpportunityEntity merged on merged.id = d.id.opportunityId where merged.mergedInto = o.id) "
          + "and not exists (select d.id from DailySelectionEntity d where d.id.opportunityId = o.id) "
          + "order by o.fitScore desc, o.createdAt, o.id")
  List<String> secondary();

  @Query("select o from OpportunityEntity o where o.id = :id")
  java.util.Optional<OpportunityEntity> findOpportunity(@Param("id") String id);
}
