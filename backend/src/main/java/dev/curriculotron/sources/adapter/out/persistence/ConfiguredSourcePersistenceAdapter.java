package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.sources.ConfiguredSourceRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class ConfiguredSourcePersistenceAdapter implements ConfiguredSourceRepository {
  private final EntityManager em;

  ConfiguredSourcePersistenceAdapter(EntityManager em) {
    this.em = em;
  }

  public List<Board> boards() {
    List<?> rows =
        em.createNativeQuery(
                "SELECT name,careers_adapter,junior_justification FROM company WHERE careers_adapter IS NOT NULL ORDER BY name")
            .getResultList();
    return rows.stream()
        .map(row -> {
          var r = (Object[]) row;
          return new Board((String) r[0], (String) r[1], (String) r[2]);
        })
        .toList();
  }
}
