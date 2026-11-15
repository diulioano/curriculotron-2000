package dev.curriculotron.applications;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApplicationStatusChangeJpaRepository extends JpaRepository<StoredStatusChange, String> {
  @Query(
      "select new dev.curriculotron.applications.ApplicationHistoryRow("
          + "s.id, s.toStatus, s.author, s.reason, s.at, e.ref, e.quote) "
          + "from StoredStatusChange s left join StoredEvidence e on e.id = s.evidenceId "
          + "where s.applicationId = :applicationId order by s.at, s.id")
  List<ApplicationHistoryRow> findHistory(@Param("applicationId") String applicationId);
}
