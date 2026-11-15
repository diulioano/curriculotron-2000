package dev.curriculotron.opportunities.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity_match_candidate")
@IdClass(OpportunityMatchCandidateId.class)
class OpportunityMatchCandidateEntity {
  @Id
  @Column(name = "opportunity_a", nullable = false)
  private String opportunityA;

  @Id
  @Column(name = "opportunity_b", nullable = false)
  private String opportunityB;

  @Column(nullable = false)
  private String signal;

  @Column(nullable = false)
  private String state;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  protected OpportunityMatchCandidateEntity() {}

  OpportunityMatchCandidateEntity(String a, String b, String signal, String at) {
    opportunityA = a;
    opportunityB = b;
    this.signal = signal;
    state = "open";
    createdAt = at;
    updatedAt = at;
  }

  String opportunityA() {
    return opportunityA;
  }

  String opportunityB() {
    return opportunityB;
  }

  String signal() {
    return signal;
  }
}
