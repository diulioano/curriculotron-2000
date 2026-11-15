package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.shared.Ids;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class OpportunityPersistenceAdapter implements OpportunityRepository {
  private final OpportunityJpaRepository opportunities;

  OpportunityPersistenceAdapter(OpportunityJpaRepository opportunities) {
    this.opportunities = opportunities;
  }

  @Override
  public Optional<dev.curriculotron.opportunities.domain.Opportunity> find(String id) {
    return opportunities.findById(id).map(OpportunityEntity::toDomain);
  }

  @Override
  public List<dev.curriculotron.opportunities.domain.Opportunity> discarded() {
    return opportunities
        .findByMergedIntoIsNullAndUserDecisionOrderByUpdatedAtDescIdAsc("discarded")
        .stream()
        .map(OpportunityEntity::toDomain)
        .toList();
  }

  @Override
  public List<String> activeIds() {
    return opportunities.findByMergedIntoIsNullOrderByIdAsc().stream()
        .map(OpportunityEntity::toDomain)
        .map(dev.curriculotron.opportunities.domain.Opportunity::id)
        .toList();
  }

  @Override
  public String createManual(String text, String at) {
    String title =
        text == null || text.isBlank() ? "Vaga a revisar" : text.lines().findFirst().orElseThrow();
    if (title.length() > 200) title = title.substring(0, 200);
    String id = Ids.newId();
    opportunities.save(OpportunityEntity.manual(id, text, title, at));
    return id;
  }

  @Override
  public String createFromSource(SourcedOpportunity source, String at) {
    String id = Ids.newId();
    opportunities.save(OpportunityEntity.sourced(id, source, source.company(), at));
    return id;
  }

  @Override
  public boolean setDecision(String id, String decision, String at) {
    if (!List.of("none", "discarded").contains(decision)) {
      throw new IllegalArgumentException("Decisão inválida.");
    }
    return opportunities.updateDecision(id, decision, at) == 1;
  }

  public void refreshDetails(String id, String title, String location, String workMode, String at) {
    var entity = opportunities.findById(id).orElseThrow();
    entity.refreshDetails(title, location, workMode, at);
    opportunities.saveAndFlush(entity);
  }
}
