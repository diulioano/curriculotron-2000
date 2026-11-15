package dev.curriculotron.audit.application.port.out;

import dev.curriculotron.audit.domain.IntegrationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Porta de saída do estado das integrações. */
public interface IntegrationStatusRepository {

  Optional<IntegrationStatus> find(String integration);

  List<IntegrationStatus> findAll();

  void save(IntegrationStatus status);

  /** Última atualização bem-sucedida real entre todas as integrações. */
  Optional<Instant> latestSuccess();
}
