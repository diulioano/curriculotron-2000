package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import java.math.BigDecimal;

/** Current search criteria (RB-001..RB-005) used to rank vacancies found by the search. */
public interface SearchCriteriaRepository {
  record Settings(RuleBasedRanker.Criteria criteria, BigDecimal cltMinimum, BigDecimal pjMinimum) {}

  Settings current();
}
