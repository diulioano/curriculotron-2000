package dev.curriculotron.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Lista de textos guardada como array JSON em coluna TEXT. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> value) {
    return JSON.writeValueAsString(value == null ? List.of() : value);
  }

  @Override
  public List<String> convertToEntityAttribute(String column) {
    return column == null ? List.of() : List.copyOf(JSON.readValue(column, TYPE));
  }
}
