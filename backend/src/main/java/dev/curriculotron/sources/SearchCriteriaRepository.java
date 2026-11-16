package dev.curriculotron.sources;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import java.math.BigDecimal;
import java.time.LocalTime;

/** Current search criteria (RB-001..RB-005) used to rank vacancies found by the search. */
public interface SearchCriteriaRepository {
  record Settings(
      RuleBasedRanker.Criteria criteria,
      BigDecimal cltMinimum,
      BigDecimal pjMinimum,
      LocalTime dailySearchTime,
      long version) {
    public Settings(
        RuleBasedRanker.Criteria criteria,
        BigDecimal cltMinimum,
        BigDecimal pjMinimum,
        LocalTime dailySearchTime) {
      this(criteria, cltMinimum, pjMinimum, dailySearchTime, 1);
    }
  }

  Settings current();
}
