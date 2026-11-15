package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.MatchCandidateRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MatchCandidatePersistenceAdapter implements MatchCandidateRepository {
  private final EntityManager em;

  MatchCandidatePersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  public List<Candidate> open() {
    List<?> rows =
        em.createNativeQuery(
                "SELECT opportunity_a,opportunity_b,signal FROM opportunity_match_candidate WHERE state='open' ORDER BY created_at,opportunity_a,opportunity_b")
            .getResultList();
    return rows.stream()
        .map(row -> {
          var r = (Object[]) row;
          return new Candidate(r[0] + ":" + r[1], (String) r[0], (String) r[1], (String) r[2]);
        })
        .toList();
  }

  public boolean reject(String a, String b, String at) {
    return em.createNativeQuery(
                "UPDATE opportunity_match_candidate SET state='rejected',updated_at=:at WHERE opportunity_a=:a AND opportunity_b=:b AND state='open'")
            .setParameter("a", a)
            .setParameter("b", b)
            .setParameter("at", at)
            .executeUpdate()
        == 1;
  }

  public boolean merge(String a, String b, String at) {
    int changed =
        em.createNativeQuery(
                "UPDATE opportunity_match_candidate SET state='merged_by_user',updated_at=:at WHERE opportunity_a=:a AND opportunity_b=:b AND state='open' AND NOT EXISTS (SELECT 1 FROM opportunity WHERE id IN (:a,:b) AND (merged_into IS NOT NULL OR user_decision='applied'))")
            .setParameter("a", a)
            .setParameter("b", b)
            .setParameter("at", at)
            .executeUpdate();
    if (changed == 0) return false;
    // Keep both records and daily selection slots as history; move source identities to the
    // survivor.
    em.createNativeQuery(
            "UPDATE opportunity SET user_decision=CASE WHEN user_decision='discarded' OR (SELECT user_decision FROM opportunity WHERE id=:b)='discarded' THEN 'discarded' ELSE user_decision END,updated_at=:at WHERE id=:a")
        .setParameter("a", a)
        .setParameter("b", b)
        .setParameter("at", at)
        .executeUpdate();
    em.createNativeQuery("UPDATE opportunity SET merged_into=:a,updated_at=:at WHERE id=:b")
        .setParameter("a", a)
        .setParameter("b", b)
        .setParameter("at", at)
        .executeUpdate();
    for (String table :
        List.of(
            "opportunity_source", "opportunity_url_identity", "opportunity_external_identity")) {
      em.createNativeQuery("UPDATE " + table + " SET opportunity_id=:a WHERE opportunity_id=:b")
          .setParameter("a", a)
          .setParameter("b", b)
          .executeUpdate();
    }
    em.createNativeQuery(
            "UPDATE opportunity_match_candidate SET state='rejected',updated_at=:at WHERE state='open' AND (opportunity_a=:b OR opportunity_b=:b)")
        .setParameter("at", at)
        .setParameter("b", b)
        .executeUpdate();
    return true;
  }
}
