package dev.curriculotron.applications;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class DuePrecisionConverter implements AttributeConverter<DuePrecision, String> {
  @Override
  public String convertToDatabaseColumn(DuePrecision precision) {
    return precision == null ? null : precision.wireValue();
  }

  @Override
  public DuePrecision convertToEntityAttribute(String value) {
    return value == null ? null : DuePrecision.fromWireValue(value);
  }
}
