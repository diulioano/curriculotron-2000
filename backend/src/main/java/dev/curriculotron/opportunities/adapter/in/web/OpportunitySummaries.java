package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resumo de vaga para listas e página inicial: a vaga mais a fonte consultada por último (`source:
 * {source, url, fetchedAt} | null`), para o cartão mostrar de onde veio e quando.
 */
@Component
public class OpportunitySummaries {
  @org.springframework.beans.factory.annotation.Autowired
  private dev.curriculotron.sources.PublicationStore publications;

  private final OpportunitySourceRepository sources;
  private final JsonMapper json = JsonMapper.builder().build();

  public OpportunitySummaries(OpportunitySourceRepository sources) {
    this.sources = sources;
  }

  public List<Map<String, Object>> of(List<Opportunity> items) {
    return items.stream().map(this::of).toList();
  }

  public Map<String, Object> of(Opportunity item) {
    Map<String, Object> result =
        json.convertValue(item, new TypeReference<LinkedHashMap<String, Object>>() {});
    result.put("publishedAt", publications.find(item.id()));
    var found = sources.forOpportunity(item.id());
    result.put("source", found.isEmpty() ? null : found.getLast());
    return result;
  }
}
