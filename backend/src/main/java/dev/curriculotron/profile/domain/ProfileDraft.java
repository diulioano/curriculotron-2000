package dev.curriculotron.profile.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Rascunho editável lido de um Markdown (US006). Pode estar incompleto: só vira {@link
 * CareerProfile} quando a pessoa revisa e salva.
 */
public record ProfileDraft(
    String role,
    String primaryTechnology,
    List<String> secondaryTechnologies,
    List<String> flexibleTechnologies,
    Seniority currentSeniority,
    List<Seniority> targetLevels,
    List<String> priorities,
    BigDecimal juniorFloorCltMonthlyBrl,
    BigDecimal juniorFloorPjMonthlyBrl) {

  public ProfileDraft {
    secondaryTechnologies = List.copyOf(secondaryTechnologies);
    flexibleTechnologies = List.copyOf(flexibleTechnologies);
    targetLevels = List.copyOf(targetLevels);
    priorities = List.copyOf(priorities);
  }

  public enum Field {
    role,
    primaryTechnology,
    secondaryTechnologies,
    flexibleTechnologies,
    currentSeniority,
    targetLevels,
    priorities,
    juniorFloorCltMonthlyBrl,
    juniorFloorPjMonthlyBrl
  }

  public enum WarningCode {
    missing,
    inferred,
    invalid
  }

  public record Warning(Field field, WarningCode code, String message) {}

  /**
   * @param provenance origem e linha de evidência de cada campo preenchido; o Markdown em si não é
   *     devolvido nem guardado
   */
  public record Preview(
      ProfileDraft draft, List<Warning> warnings, Map<Field, FieldProvenance> provenance) {
    public Preview {
      warnings = List.copyOf(warnings);
      provenance = FieldProvenance.copy(provenance);
    }
  }
}
