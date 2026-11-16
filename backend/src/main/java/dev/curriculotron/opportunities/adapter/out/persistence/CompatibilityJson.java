package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.CompatibilityAssessment.CriteriaSnapshot;
import dev.curriculotron.opportunities.domain.CompatibilityAssessment.EvidenceItem;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class CompatibilityJson {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private CompatibilityJson() {}

  static String write(Object value) {
    return JSON.writeValueAsString(value);
  }

  static List<String> strings(String value) {
    return List.copyOf(JSON.readValue(value, new TypeReference<List<String>>() {}));
  }

  static List<EvidenceItem> evidence(String value) {
    return List.copyOf(JSON.readValue(value, new TypeReference<List<EvidenceItem>>() {}));
  }

  static CriteriaSnapshot criteria(String value) {
    return JSON.readValue(value, CriteriaSnapshot.class);
  }
}
