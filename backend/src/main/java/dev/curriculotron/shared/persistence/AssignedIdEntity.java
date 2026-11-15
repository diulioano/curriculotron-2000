package dev.curriculotron.shared.persistence;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base para entidades com id atribuído pela aplicação (UUIDv7). Sem isso, {@code save()} faria um
 * {@code SELECT} antes de cada {@code INSERT} para decidir entre persist e merge.
 */
@MappedSuperclass
public abstract class AssignedIdEntity<ID> implements Persistable<ID> {

  @Transient private boolean isNew = true;

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    isNew = false;
  }
}
