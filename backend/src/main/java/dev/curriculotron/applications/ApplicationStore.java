package dev.curriculotron.applications;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Repository;

/** JPA adapter for applications and their immutable status history. */
@Repository
public class ApplicationStore {
  private final ApplicationJpaRepository applications;
  private final ApplicationStatusChangeJpaRepository statusChanges;
  private final OpportunityApplicationJpaRepository opportunities;

  ApplicationStore(
      ApplicationJpaRepository applications,
      ApplicationStatusChangeJpaRepository statusChanges,
      OpportunityApplicationJpaRepository opportunities) {
    this.applications = applications;
    this.statusChanges = statusChanges;
    this.opportunities = opportunities;
  }

  public List<ApplicationSummary> list() {
    return applications.findSummaries();
  }

  public ApplicationSummary find(String id) {
    return applications
        .findSummary(id)
        .orElseThrow(() -> new NoSuchElementException("Candidatura não encontrada."));
  }

  StoredApplication findForUpdate(String id) {
    return applications
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Candidatura não encontrada."));
  }

  public List<ApplicationHistory> history(String id) {
    return statusChanges.findHistory(id).stream()
        .map(row ->
            new ApplicationHistory(
                row.id(),
                row.toStatus(),
                row.author(),
                row.reason(),
                row.at(),
                row.evidenceRef() == null
                    ? null
                    : new ApplicationHistory.EvidenceSummary(
                        row.evidenceRef(), row.evidenceQuote())))
        .toList();
  }

  public void save(StoredApplication application) {
    applications.save(application);
  }

  public void append(StoredStatusChange statusChange) {
    statusChanges.save(statusChange);
  }

  public void markApplied(String opportunityId, String at) {
    if (opportunities.markApplied(opportunityId, at) != 1) {
      throw new NoSuchElementException("Vaga não encontrada.");
    }
  }
}
