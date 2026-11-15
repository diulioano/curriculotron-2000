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
