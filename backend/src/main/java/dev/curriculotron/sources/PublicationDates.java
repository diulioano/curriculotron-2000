package dev.curriculotron.sources;

import java.time.*;

final class PublicationDates {
  private PublicationDates() {}

  static LocalDate parse(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      if (value.matches("[0-9]{13}"))
        return Instant.ofEpochMilli(Long.parseLong(value))
            .atZone(dev.curriculotron.shared.AppClock.ZONE)
            .toLocalDate();
      return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
