package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.application.port.out.OpportunityRepository;
import dev.curriculotron.opportunities.domain.Opportunity;
import dev.curriculotron.shared.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "opportunity")
class OpportunityEntity {
  @Id private String id;

  @Column(name = "evidence_revision", nullable = false)
  private long evidenceRevision = 1;

  @Column(nullable = false)
  private String title;

  @Column(name = "company_name", nullable = false)
  private String companyName;

  private String location;

  @Column(name = "work_mode", nullable = false)
  private String workMode;

  @Column(name = "contract_type", nullable = false)
  private String contractType;

  @Column(name = "salary_min")
  private BigDecimal salaryMin;

  @Column(name = "salary_max")
  private BigDecimal salaryMax;

  @Column(name = "salary_unit", nullable = false)
  private String salaryUnit;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "requirements_facts", nullable = false)
  private List<String> facts;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "requirements_inferred", nullable = false)
  private List<String> inferred;

  @Column(nullable = false)
  private String availability;

  @Column(nullable = false)
  private String fit;

  @Column(name = "fit_score", nullable = false)
  private int fitScore;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "fit_reasons", nullable = false)
  private List<String> fitReasons;

  @Column(name = "user_decision", nullable = false)
  private String userDecision;

  @Column(name = "decision_reverted_at")
  private String decisionRevertedAt;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  @Column(name = "merged_into")
  private String mergedInto;

  protected OpportunityEntity() {}

  static OpportunityEntity manual(String id, String text, String title, String at) {
    var entity = new OpportunityEntity();
    entity.id = id;
    entity.title = title;
    entity.companyName = "Empresa a confirmar";
    entity.workMode = "unknown";
    entity.contractType = "unknown";
    entity.salaryUnit = "unknown";
    entity.facts = text == null ? List.of() : List.of(text);
    entity.inferred = List.of();
    entity.availability = "unconfirmed";
    entity.fit = "weak";
    entity.fitReasons = List.of();
    entity.userDecision = "none";
    entity.createdAt = at;
    entity.updatedAt = at;
    return entity;
  }

  static OpportunityEntity sourced(
      String id, OpportunityRepository.SourcedOpportunity source, String company, String at) {
    var entity = new OpportunityEntity();
    entity.id = id;
    entity.title = source.title();
    entity.companyName = company == null ? "Empresa a confirmar" : company;
    entity.location = source.location();
    entity.workMode = source.workMode();
    entity.contractType = "unknown";
    entity.salaryUnit = "unknown";
    entity.facts = source.requirementFacts();
    entity.inferred = List.of();
    entity.availability = "open";
    entity.fit = source.fit();
    entity.fitScore = source.fitScore();
    entity.fitReasons = source.fitReasons();
    entity.userDecision = "none";
    entity.createdAt = at;
    entity.updatedAt = at;
    return entity;
  }

  Opportunity toDomain() {
    Opportunity.Salary salary =
        salaryMin == null && salaryMax == null
            ? null
            : new Opportunity.Salary(salaryMin, salaryMax, salaryUnit);
    return new Opportunity(
        id,
        title,
        companyName,
        location,
        workMode,
        contractType,
        salary,
        facts,
        inferred,
        fitReasons,
        userDecision,
        availability,
        mergedInto);
  }

  List<String> fitReasons() {
    return fitReasons;
  }

  void refreshDetails(String newTitle, String newLocation, String newWorkMode, String at) {
    if (!Objects.equals(title, newTitle)
        || !Objects.equals(location, newLocation)
        || !Objects.equals(workMode, newWorkMode)) {
      title = newTitle;
      location = newLocation;
      workMode = newWorkMode;
      evidenceRevision++;
      updatedAt = at;
    }
  }

  String title() {
    return title;
  }

  String companyName() {
    return companyName;
  }

  String location() {
    return location;
  }

  String mergedInto() {
    return mergedInto;
  }

  String userDecision() {
    return userDecision;
  }

  void mergeInto(String survivor, String at) {
    mergedInto = survivor;
    updatedAt = at;
  }

  void setDecision(String decision, String at) {
    userDecision = decision;
    updatedAt = at;
  }
}
