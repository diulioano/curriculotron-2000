package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.sources.ConfiguredSourceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ConfiguredSourcePersistenceAdapter implements ConfiguredSourceRepository {
  private final CompanySourceJpaRepository companies;

  ConfiguredSourcePersistenceAdapter(CompanySourceJpaRepository companies) {
    this.companies = companies;
  }

  public List<Board> boards() {
    return companies.findByCareersAdapterIsNotNullOrderByNameAsc().stream()
        .map(company ->
            new Board(company.name(), company.careersAdapter(), company.juniorJustification()))
        .toList();
  }

  public Optional<String> juniorJustification(String company) {
    return companies.findByNameIgnoreCase(company).stream()
        .map(CompanySourceEntity::juniorJustification)
        .filter(reason -> reason != null && !reason.isBlank())
        .findFirst();
  }
}
