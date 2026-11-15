package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.shared.persistence.StringListJsonConverter;
import dev.curriculotron.sources.SearchCriteriaRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class SearchCriteriaPersistenceAdapter implements SearchCriteriaRepository {
  private final EntityManager em;
  private final StringListJsonConverter json = new StringListJsonConverter();

  SearchCriteriaPersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  public Settings current() {
    var r =
        (Object[])
            em.createNativeQuery(
                    "SELECT target_roles,stack_keywords,hybrid_regions,remote_preferred,accept_junior_if_justified,clt_min_monthly_brl,pj_min_monthly_brl FROM search_criteria WHERE id=1")
                .getSingleResult();
    @SuppressWarnings("unchecked")
    List<String> priority =
        em.createNativeQuery("SELECT name FROM company WHERE tier='priority'", String.class)
            .getResultList();
    return new Settings(
        new RuleBasedRanker.Criteria(
            new HashSet<>(json.convertToEntityAttribute((String) r[0])),
            new HashSet<>(json.convertToEntityAttribute((String) r[1])),
            new HashSet<>(json.convertToEntityAttribute((String) r[2])),
            new HashSet<>(priority),
            truthy(r[3]),
            truthy(r[4])),
        new BigDecimal(r[5].toString()),
        new BigDecimal(r[6].toString()));
  }

  private static boolean truthy(Object value) {
    return value != null && !"0".equals(value.toString()) && !"false".equals(value.toString());
  }
}
