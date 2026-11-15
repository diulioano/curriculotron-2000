package dev.curriculotron.sources;

import dev.curriculotron.shared.persistence.WriteExecutor;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PublicationStore {
  private final OpportunityPublicationJpaRepository publications;
  private final WriteExecutor writer;

  public PublicationStore(OpportunityPublicationJpaRepository publications, WriteExecutor writer) {
    this.publications = publications;
    this.writer = writer;
  }

  public void record(String id, LocalDate date) {
    if (date != null) writer.run(() -> publications.updatePublishedAt(id, date.toString()));
  }

  public String find(String id) {
    return publications.findById(id).map(OpportunityPublicationEntity::publishedAt).orElse(null);
  }
}
