package dev.curriculotron.sources;

import java.time.LocalDate;

/** Inclusive publication dates, evaluated in the installation's local timezone. */
public record SearchWindow(LocalDate publishedFrom, LocalDate publishedTo) {
  public SearchWindow {
    if (publishedFrom == null || publishedTo == null || publishedFrom.isAfter(publishedTo))
      throw new IllegalArgumentException("Período de busca inválido.");
  }

  public static SearchWindow resolve(LocalDate from, LocalDate to, LocalDate today) {
    LocalDate end = to == null ? today : to;
    return new SearchWindow(from == null ? end.minusDays(30) : from, end);
  }

  public boolean includes(LocalDate publication) {
    return publication == null
        || (!publication.isBefore(publishedFrom) && !publication.isAfter(publishedTo));
  }
}
