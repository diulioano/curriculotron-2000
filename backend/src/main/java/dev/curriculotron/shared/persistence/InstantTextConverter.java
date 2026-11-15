package dev.curriculotron.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * {@code *_at} em UTC ISO-8601 com milissegundos fixos ({@code 2026-11-14T10:00:00.000Z}).
 *
 * <p>A largura fixa mantém a ordem lexicográfica igual à cronológica, o que as consultas por
 * intervalo ({@code at >= ?}, {@code next_run_at <= ?}) exigem em colunas TEXT do SQLite.
 */
@Converter(autoApply = true)
public class InstantTextConverter implements AttributeConverter<Instant, String> {

  private static final DateTimeFormatter FORMAT =
      new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

  @Override
  public String convertToDatabaseColumn(Instant value) {
    return value == null ? null : FORMAT.format(value);
  }

  @Override
  public Instant convertToEntityAttribute(String column) {
    return column == null ? null : Instant.parse(column);
  }
}
