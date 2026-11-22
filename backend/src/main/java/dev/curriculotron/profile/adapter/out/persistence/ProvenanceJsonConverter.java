package dev.curriculotron.profile.adapter.out.persistence;

import dev.curriculotron.profile.domain.FieldProvenance;
import dev.curriculotron.profile.domain.ProfileDraft;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Origem por campo do perfil guardada como objeto JSON em coluna TEXT. */
@Converter
public class ProvenanceJsonConverter
    implements AttributeConverter<Map<ProfileDraft.Field, FieldProvenance>, String> {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<Map<ProfileDraft.Field, FieldProvenance>> TYPE =
      new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<ProfileDraft.Field, FieldProvenance> value) {
    return JSON.writeValueAsString(FieldProvenance.copy(value));
  }

  @Override
  public Map<ProfileDraft.Field, FieldProvenance> convertToEntityAttribute(String column) {
    return column == null ? Map.of() : FieldProvenance.copy(JSON.readValue(column, TYPE));
  }
}
