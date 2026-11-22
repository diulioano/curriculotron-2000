package dev.curriculotron.sources.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company")
class CompanySourceEntity {
  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String tier;

  @Column(name = "careers_adapter")
  private String careersAdapter;

  @Column(name = "junior_justification")
  private String juniorJustification;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  @Column(name = "promoted_by_user_at")
  private String promotedAt;

  static CompanySourceEntity priority(String name, java.time.Instant at) {
    var company = new CompanySourceEntity();
    company.id = dev.curriculotron.shared.Ids.newId();
    company.name = name;
    company.createdAt = at.toString();
    company.setPriority(true, at);
    return company;
  }

  void setPriority(boolean priority, java.time.Instant at) {
    String next = priority ? "priority" : "suggested";
    if (next.equals(tier)) return;
    tier = next;
    if (priority) promotedAt = at.toString();
    updatedAt = at.toString();
  }

  protected CompanySourceEntity() {}

  String name() {
    return name;
  }

  String juniorJustification() {
    return juniorJustification;
  }

  String careersAdapter() {
    return careersAdapter;
  }
}
