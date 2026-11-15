package dev.curriculotron.profile.adapter.out.persistence;

import dev.curriculotron.profile.domain.FactKind;
import dev.curriculotron.profile.domain.FactOrigin;
import dev.curriculotron.profile.domain.ProfileFact;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "profile_fact")
class ProfileFactEntity extends AssignedIdEntity<String> {

  @Id private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "profile_version_id")
  private ProfileVersionEntity version;

  @Enumerated(EnumType.STRING)
  private FactKind kind;

  private String statement;

  @Enumerated(EnumType.STRING)
  private FactOrigin origin;

  @Column(name = "origin_ref")
  private String originRef;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected ProfileFactEntity() {}

  static ProfileFactEntity from(ProfileFact f, ProfileVersionEntity version, Instant now) {
    ProfileFactEntity e = new ProfileFactEntity();
    e.id = f.id();
    e.version = version;
    e.kind = f.kind();
    e.statement = f.statement();
    e.origin = f.origin();
    e.originRef = f.originRef();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  @Override
  public String getId() {
    return id;
  }
}
