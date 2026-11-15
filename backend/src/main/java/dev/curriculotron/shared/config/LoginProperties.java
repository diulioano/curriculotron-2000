package dev.curriculotron.shared.config;

import java.time.Duration;

/** {@code curriculotron.login.*}: bloqueio temporário após tentativas erradas (D-06). */
public record LoginProperties(int maxAttempts, Duration lockDuration) {}
