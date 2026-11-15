package dev.curriculotron.orchestration.domain;

/**
 * Estados de {@code TaskRun}: {@code queued → running → completed | failed | paused}, e {@code
 * paused → running} na retomada.
 */
public enum TaskRunState {
  queued,
  running,
  completed,
  failed,
  paused;

  public boolean canTransitionTo(TaskRunState to) {
    return switch (this) {
      case queued -> to == running || to == failed || to == paused;
      case running -> to == completed || to == failed || to == paused;
      case paused -> to == running || to == failed;
      case completed, failed -> false;
    };
  }

  public boolean isTerminal() {
    return this == completed || this == failed;
  }
}
