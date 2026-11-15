package dev.curriculotron.applications;

public record ApplicationHistory(
    String id,
    ApplicationStatus toStatus,
    String author,
    String reason,
    String at,
    EvidenceSummary evidence) {
  public record EvidenceSummary(String ref, String quote) {}
}
