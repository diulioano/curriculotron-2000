package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.sources.CriteriaDocument;
import dev.curriculotron.sources.CriteriaStore;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import org.springframework.stereotype.Repository;

@Repository
class SearchCriteriaPersistenceAdapter implements SearchCriteriaRepository, CriteriaStore {
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
        LocalTime.parse(current.dailySearchTimeLocal()),
        current.document().version());
  }

  public CriteriaDocument document() {
    return criteria.findById(1).orElseThrow().document();
  }

  public void save(CriteriaDocument document, Instant at) {
    var entity = criteria.findById(1).orElseThrow();
    entity.apply(document, at.toString());
    criteria.saveAndFlush(entity);
  }
}
