package dev.curriculotron.profile.adapter.out.persistence;

import dev.curriculotron.profile.domain.CareerProfile;
import dev.curriculotron.profile.domain.FieldProvenance;
import dev.curriculotron.profile.domain.ProfileDraft;
import dev.curriculotron.profile.domain.Seniority;
import dev.curriculotron.shared.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "career_profile")
class CareerProfileEntity {
  static final int SINGLETON_ID = 1;

  @Id private Integer id;

  @Column(nullable = false)
  private long version;

  @Column(nullable = false)
  private String role;

  @Column(name = "primary_technology", nullable = false)
  private String primaryTechnology;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "secondary_technologies", nullable = false)
  private List<String> secondaryTechnologies;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "flexible_technologies", nullable = false)
  private List<String> flexibleTechnologies;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_seniority", nullable = false)
  private Seniority currentSeniority;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "target_levels", nullable = false)
  private List<String> targetLevels;

  @Convert(converter = StringListJsonConverter.class)
  @Column(nullable = false)
  private List<String> priorities;

  @Column(name = "junior_floor_clt_monthly_brl")
  private String juniorFloorClt;

  @Column(name = "junior_floor_pj_monthly_brl")
  private String juniorFloorPj;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CareerProfile.Origin origin;

  @Convert(converter = ProvenanceJsonConverter.class)
  @Column(nullable = false)
  private Map<ProfileDraft.Field, FieldProvenance> provenance;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CareerProfileEntity() {}

  static CareerProfileEntity create(Instant at) {
    var entity = new CareerProfileEntity();
    entity.id = SINGLETON_ID;
    entity.createdAt = at;
    return entity;
  }

  void apply(CareerProfile profile, Instant at) {
    version = profile.version();
    role = profile.role();
    primaryTechnology = profile.primaryTechnology();
    secondaryTechnologies = profile.secondaryTechnologies();
    flexibleTechnologies = profile.flexibleTechnologies();
    currentSeniority = profile.currentSeniority();
    targetLevels = profile.targetLevels().stream().map(Seniority::name).toList();
    priorities = profile.priorities();
    juniorFloorClt = text(profile.juniorFloorCltMonthlyBrl());
    juniorFloorPj = text(profile.juniorFloorPjMonthlyBrl());
    origin = profile.origin();
    provenance = profile.provenance();
    updatedAt = at;
  }

  CareerProfile profile() {
    return new CareerProfile(
        version,
        role,
        primaryTechnology,
        secondaryTechnologies,
        flexibleTechnologies,
        currentSeniority,
        targetLevels.stream().map(Seniority::valueOf).toList(),
        priorities,
        juniorFloorClt == null ? null : new BigDecimal(juniorFloorClt),
        juniorFloorPj == null ? null : new BigDecimal(juniorFloorPj),
        origin,
        provenance);
  }

  Instant updatedAt() {
    return updatedAt;
  }

  private static String text(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
