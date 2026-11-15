package dev.curriculotron.applications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "opportunity")
class StoredOpportunity {
  @Id private String id;

  @Column(nullable = false)
  private String title;

  @Column(name = "company_name", nullable = false)
  private String companyName;

  @Column(name = "merged_into")
  private String mergedInto;

  @Column(name = "user_decision")
  private String userDecision;

  @Column(name = "updated_at")
  private String updatedAt;

  protected StoredOpportunity() {}
}
