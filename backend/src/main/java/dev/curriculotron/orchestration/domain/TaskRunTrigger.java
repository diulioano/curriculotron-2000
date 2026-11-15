package dev.curriculotron.orchestration.domain;

/** O que originou a execução. */
public enum TaskRunTrigger {
  routine,
  manual,
  coordinator
}
