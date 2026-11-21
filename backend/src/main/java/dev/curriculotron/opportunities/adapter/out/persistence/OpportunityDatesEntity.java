package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Projeção das datas da vaga ({@code published_at}, {@code deadline_at}). Fica separada de {@link
 * OpportunityEntity} para que o UPDATE completo dela nunca sobrescreva datas gravadas por outro
 * caminho (por exemplo, a coleta que registra a publicação).
 */
@Entity
@Table(name = "opportunity")
class OpportunityDatesEntity {
  @Id private String id;

  @Column(name = "published_at")
  private String publishedAt;

  @Column(name = "deadline_at")
  private String deadlineAt;

  protected OpportunityDatesEntity() {}

  String id() {
    return id;
  }

  String publishedAt() {
    return publishedAt;
  }

  String deadlineAt() {
    return deadlineAt;
  }
}
