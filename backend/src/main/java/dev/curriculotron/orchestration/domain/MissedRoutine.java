package dev.curriculotron.orchestration.domain;

import java.time.Instant;

/** Ocorrência não executada (computador desligado) — exibida na Início ({@code missedRoutines}). */
public record MissedRoutine(String routineId, String purpose, Instant scheduledFor) {}
