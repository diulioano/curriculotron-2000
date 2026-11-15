package dev.curriculotron.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;

/**
 * Horário local em texto {@code HH:mm} (ex.: {@code daily_search_time_local}, {@code time_local}).
 */
@Converter(autoApply = true)
public class LocalTimeTextConverter implements AttributeConverter<LocalTime, String> {

  @Override
  public String convertToDatabaseColumn(LocalTime value) {
    return value == null ? null : value.toString();
  }

  @Override
  public LocalTime convertToEntityAttribute(String column) {
    return column == null ? null : LocalTime.parse(column);
  }
}
