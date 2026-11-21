package dev.curriculotron.opportunities.application.port.out;

import java.util.List;

public interface OpportunitySourceRepository {
  record Source(String source, String url, String fetchedAt) {}

  /** Primeira observação de uma fonte para a vaga ({@code firstSeenAt} é um instante ISO). */
  record Discovery(String id, String source, String firstSeenAt) {}

  List<Source> forOpportunity(String id);

  /** Descobertas da vaga em ordem cronológica crescente. */
  List<Discovery> discoveries(String id);
}
