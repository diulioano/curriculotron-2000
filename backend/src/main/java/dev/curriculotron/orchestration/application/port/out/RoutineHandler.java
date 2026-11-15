package dev.curriculotron.orchestration.application.port.out;

import dev.curriculotron.orchestration.domain.Routine;
import java.time.LocalDate;

/**
 * Executa uma rotina de um propósito (ex.: {@code search}, {@code digest}, {@code backup}). Outros
 * módulos implementam esta porta. Implementações devem ser idempotentes pela chave recebida ({@code
 * routine:<id>:<data local>}).
 */
public interface RoutineHandler {

  String purpose();

  void run(Routine routine, LocalDate localDate, String idempotencyKey);
}
