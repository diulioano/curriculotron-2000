package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.time.LocalTime;
import java.util.HashSet;
import org.springframework.stereotype.Repository;

@Repository
class SearchCriteriaPersistenceAdapter implements SearchCriteriaRepository {
  private final SearchCriteriaJpaRepository criteria;
  private final CompanySourceJpaRepository companies;

  SearchCriteriaPersistenceAdapter(
      SearchCriteriaJpaRepository criteria, CompanySourceJpaRepository companies) {
    this.criteria = criteria;
    this.companies = companies;
  }

  public Settings current() {
    var current = criteria.findById(1).orElseThrow();
    var priority =
        companies.findByTier("priority").stream().map(CompanySourceEntity::name).toList();
    return new Settings(
        new RuleBasedRanker.Criteria(
            new HashSet<>(current.targetRoles()),
            new HashSet<>(current.stackKeywords()),
            new HashSet<>(current.hybridRegions()),
            new HashSet<>(priority),
            current.remotePreferred(),
            current.acceptJuniorIfJustified()),
        current.cltMinimum(),
        current.pjMinimum(),
        LocalTime.parse(current.dailySearchTimeLocal()));
  }
}
