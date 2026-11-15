package dev.curriculotron.sources;

import dev.curriculotron.shared.persistence.WriteExecutor;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PublicationStore {
  private final EntityManager em;
  private final WriteExecutor writer;

  public PublicationStore(EntityManager em, WriteExecutor writer) {
    this.em = em;
    this.writer = writer;
  }

  public void record(String id, LocalDate date) {
    if (date != null)
      writer.run(() ->
          em.createNativeQuery("UPDATE opportunity SET published_at=:date WHERE id=:id")
              .setParameter("date", date.toString())
              .setParameter("id", id)
              .executeUpdate());
  }

  public String find(String id) {
    var rows =
        em.createNativeQuery("SELECT published_at FROM opportunity WHERE id=:id", String.class)
            .setParameter("id", id)
            .getResultList();
    return rows.isEmpty() ? null : (String) rows.getFirst();
  }
}
