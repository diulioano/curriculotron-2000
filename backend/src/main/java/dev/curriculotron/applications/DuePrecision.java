package dev.curriculotron.applications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DuePrecision {
  EXACT("exact"),
  DATE_ONLY("date_only"),
  AMBIGUOUS("ambiguous");

  private final String wireValue;

  DuePrecision(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator
  public static DuePrecision fromWireValue(String value) {
    for (var precision : values()) {
      if (precision.wireValue.equals(value)) return precision;
    }
    throw new IllegalArgumentException("Precisão de prazo inválida.");
  }
}
