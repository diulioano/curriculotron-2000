package dev.curriculotron.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Objeto JSON livre (ex.: checkpoint de TaskRun) guardado em coluna TEXT. */
@Converter
public class JsonObjectConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<LinkedHashMap<String, Object>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, Object> value) {
    return JSON.writeValueAsString(value == null ? Map.of() : value);
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String column) {
    return column == null ? Map.of() : Collections.unmodifiableMap(JSON.readValue(column, TYPE));
  }
}
