package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.RuleBasedRanker;
import dev.curriculotron.profile.domain.CareerProfile;
import dev.curriculotron.sources.CriteriaDocument;
import dev.curriculotron.sources.CriteriaStore;
import dev.curriculotron.sources.SearchCriteriaRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    var priority = priorityCompanies();
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

  @Override
  public java.util.List<String> priorityCompanies() {
    return companies.findByTier("priority").stream()
        .map(CompanySourceEntity::name)
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
  }

  @Override
  public void replacePriorityCompanies(java.util.List<String> names, Instant at) {
    var remaining = new LinkedHashMap<String, String>();
    for (String name : names) {
      if (name != null && !name.isBlank()) {
        remaining.putIfAbsent(CareerProfile.key(name), name.strip());
      }
    }
    for (var company : companies.findAll()) {
      company.setPriority(remaining.remove(CareerProfile.key(company.name())) != null, at);
    }
    for (String name : remaining.values()) {
      companies.save(CompanySourceEntity.priority(name, at));
    }
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
