package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.VacancyEvidence.Completeness;
import java.util.List;
import java.util.Objects;

/** A source supplies evidence; extraction and ranking happen separately. */
public interface JobSource {
  SourceResult fetch();

  /**
   * URL may be absent for pasted text; external ID may be absent for manual input. Title and
   * location are copied literally from structured source fields when the source exposes them.
   */
  record Item(
      String source,
      String externalId,
      String url,
      String text,
      String title,
      String location,
      java.time.LocalDate publishedAt,
      Completeness completeness,
      String completenessReason) {
    public Item {
      Objects.requireNonNull(completeness);
      if (text != null && text.length() > 100_000) {
        int end = 100_000;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        text = text.substring(0, end);
        completeness = Completeness.partial;
        completenessReason = "locally_truncated";
      }
    }

    public Item(
        String source,
        String externalId,
        String url,
        String text,
        String title,
        String location,
        java.time.LocalDate publishedAt) {
      this(
          source,
          externalId,
          url,
          text,
          title,
          location,
          publishedAt,
          Completeness.unknown,
          "completeness_not_provided");
    }

    public Item(
        String source, String externalId, String url, String text, String title, String location) {
      this(source, externalId, url, text, title, location, null);
    }

    public Item(String source, String externalId, String url, String text) {
      this(source, externalId, url, text, null, null);
    }
  }

  record Failure(String source, String code, String message) {}

  record SourceResult(List<Item> items, List<Failure> failures, List<String> limitations) {
    public SourceResult {
      items = List.copyOf(items);
      failures = List.copyOf(failures);
      limitations = List.copyOf(limitations);
    }

    static SourceResult failed(String source, Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        return new SourceResult(
            List.of(),
            List.of(new Failure(source, "interrupted", "Consulta interrompida.")),
            List.of());
      }
      return new SourceResult(
          List.of(),
          List.of(
              new Failure(source, "unavailable", "Fonte indisponível ou formato não reconhecido.")),
          List.of());
    }
  }
}
