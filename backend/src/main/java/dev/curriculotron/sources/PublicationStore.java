package dev.curriculotron.sources;

import dev.curriculotron.opportunities.application.port.out.OpportunityCorrectionRepository;
import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PublicationStore {
  private final OpportunityPublicationJpaRepository publications;
  private final OpportunityCorrectionRepository corrections;
  private final WriteExecutor writer;

  public PublicationStore(
      OpportunityPublicationJpaRepository publications,
      OpportunityCorrectionRepository corrections,
      WriteExecutor writer) {
    this.publications = publications;
    this.corrections = corrections;
    this.writer = writer;
  }

  /** Registra a data coletada, exceto quando a pessoa já corrigiu a publicação desta vaga. */
  public void record(String id, LocalDate date) {
    if (date == null) return;
    writer.run(() -> {
      if (corrections.correctedFields(id).contains(CorrectableOpportunityField.published_at))
        return;
      publications.updatePublishedAt(id, date.toString());
    });
  }

  public String find(String id) {
    return publications.findById(id).map(OpportunityPublicationEntity::publishedAt).orElse(null);
  }
}
