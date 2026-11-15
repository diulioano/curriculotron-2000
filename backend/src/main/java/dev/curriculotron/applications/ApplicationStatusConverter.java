package dev.curriculotron.applications;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class ApplicationStatusConverter implements AttributeConverter<ApplicationStatus, String> {
  @Override
  public String convertToDatabaseColumn(ApplicationStatus status) {
    return status == null ? null : status.wireValue();
  }

  @Override
  public ApplicationStatus convertToEntityAttribute(String value) {
    return value == null ? null : ApplicationStatus.fromWireValue(value);
  }
}
