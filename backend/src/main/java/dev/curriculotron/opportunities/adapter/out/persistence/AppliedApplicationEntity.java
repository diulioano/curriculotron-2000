package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Projeção somente leitura da tabela {@code application} (id, vaga e confirmação). A escrita
 * pertence ao módulo de candidaturas.
 */
@Entity
@Immutable
@Table(name = "application")
class AppliedApplicationEntity {
  @Id private String id;

  @Column(name = "opportunity_id", nullable = false)
  private String opportunityId;

  @Column(name = "confirmed_at", nullable = false)
  private String confirmedAt;

  protected AppliedApplicationEntity() {}

  String id() {
    return id;
  }

  String confirmedAt() {
    return confirmedAt;
  }
}
