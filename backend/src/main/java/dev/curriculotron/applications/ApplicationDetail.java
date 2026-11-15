package dev.curriculotron.applications;

import java.util.List;

public record ApplicationDetail(
    String id,
    String opportunityId,
    String title,
    String companyName,
    ApplicationStatus status,
    String nextAction,
    String nextActionDueAt,
    DuePrecision duePrecision,
    String confirmationOrigin,
    String updatedAt,
    List<ApplicationHistory> history) {
  public ApplicationDetail {
    history = List.copyOf(history);
  }
}
