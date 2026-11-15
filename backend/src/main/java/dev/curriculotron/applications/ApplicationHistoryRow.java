package dev.curriculotron.applications;

public record ApplicationHistoryRow(
    String id,
    ApplicationStatus toStatus,
    String author,
    String reason,
    String at,
    String evidenceRef,
    String evidenceQuote) {}
