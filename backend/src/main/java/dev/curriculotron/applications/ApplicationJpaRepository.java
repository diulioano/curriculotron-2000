package dev.curriculotron.applications;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApplicationJpaRepository extends JpaRepository<StoredApplication, String> {
  String SUMMARY_QUERY =
      "select new dev.curriculotron.applications.ApplicationSummary("
          + "a.id, a.opportunityId, o.title, o.companyName, a.status, a.nextAction, "
          + "a.nextActionDueAt, a.duePrecision, a.confirmationOrigin, a.updatedAt) "
          + "from StoredApplication a join StoredOpportunity o on o.id = a.opportunityId";

  @Query(
      SUMMARY_QUERY
          + " order by case when a.nextActionDueAt is null then 1 else 0 end, a.nextActionDueAt, a.id")
  List<ApplicationSummary> findSummaries();

  @Query(SUMMARY_QUERY + " where a.id = :id")
  Optional<ApplicationSummary> findSummary(@Param("id") String id);
}
