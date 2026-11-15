package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.OpportunitySourceRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class OpportunitySourcePersistenceAdapter implements OpportunitySourceRepository {
  private final EntityManager em;

  OpportunitySourcePersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  public List<Source> forOpportunity(String id) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT source,canonical_url,fetched_at FROM opportunity_source WHERE opportunity_id=:id ORDER BY fetched_at,id")
            .setParameter("id", id)
            .getResultList();
    return rows.stream()
        .map(row -> {
          Object[] r = (Object[]) row;
          return new Source((String) r[0], (String) r[1], (String) r[2]);
        })
        .toList();
  }
}
