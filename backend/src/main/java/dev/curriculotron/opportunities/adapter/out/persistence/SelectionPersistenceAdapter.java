package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.SelectionRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class SelectionPersistenceAdapter implements SelectionRepository {
  private final EntityManager em;
  private static final String UNSELECTED =
      """
      FROM opportunity o WHERE o.user_decision = 'none' AND o.availability <> 'closed'
      AND o.merged_into IS NULL
      AND NOT EXISTS (SELECT 1 FROM daily_selection d JOIN opportunity m ON m.id=d.opportunity_id WHERE m.merged_into=o.id)
      AND NOT EXISTS (SELECT 1 FROM daily_selection d WHERE d.opportunity_id = o.id)
      """;

  SelectionPersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<String> selected(LocalDate date) {
    return em.createNativeQuery(
            "SELECT opportunity_id FROM daily_selection WHERE local_date = :date ORDER BY rank",
            String.class)
        .setParameter("date", date.toString())
        .getResultList();
  }

  @Override
  public List<String> eligible(int limit) {
    return em.createNativeQuery(
            "SELECT o.id "
                + UNSELECTED
                + " AND o.fit = 'strong' ORDER BY o.fit_score DESC, o.created_at, o.id",
            String.class)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public List<String> secondary() {
    return em.createNativeQuery(
            "SELECT o.id " + UNSELECTED + " ORDER BY o.fit_score DESC, o.created_at, o.id",
            String.class)
        .getResultList();
  }

  @Override
  public void select(LocalDate date, String id, int rank) {
    em.createNativeQuery(
            """
            INSERT INTO daily_selection(local_date, opportunity_id, rank, reason)
            SELECT :date, id, :rank, fit_reasons FROM opportunity WHERE id = :id
            """)
        .setParameter("date", date.toString())
        .setParameter("id", id)
        .setParameter("rank", rank)
        .executeUpdate();
  }
}
