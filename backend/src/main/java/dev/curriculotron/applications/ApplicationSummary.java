package dev.curriculotron.applications;

public record ApplicationSummary(
    String id,
    String opportunityId,
    String title,
    String companyName,
    ApplicationStatus status,
    String nextAction,
    String nextActionDueAt,
    DuePrecision duePrecision,
    String confirmationOrigin,
    String updatedAt) {}
