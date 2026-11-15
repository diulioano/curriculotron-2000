package dev.curriculotron.applications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ApplicationStatus {
  SUBMITTED_AWAITING("submitted_awaiting"),
  CONTACT_RECEIVED("contact_received"),
  INTERVIEW("interview"),
  OFFER("offer"),
  CLOSED_REJECTED("closed_rejected"),
  WITHDRAWN("withdrawn");

  private final String wireValue;

  ApplicationStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator
  public static ApplicationStatus fromWireValue(String value) {
    for (var status : values()) {
      if (status.wireValue.equals(value)) return status;
    }
    throw new IllegalArgumentException("Etapa de candidatura inválida.");
  }
}
