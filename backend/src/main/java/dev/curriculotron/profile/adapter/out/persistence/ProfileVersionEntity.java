package dev.curriculotron.profile.adapter.out.persistence;

import dev.curriculotron.profile.domain.ProfileStatus;
import dev.curriculotron.profile.domain.ProfileVersion;
import dev.curriculotron.shared.persistence.AssignedIdEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "profile_version")
class ProfileVersionEntity extends AssignedIdEntity<String> {

  @Id private String id;

  @Column(name = "source_file")
  private String sourceFile;

  @Column(name = "source_hash")
  private String sourceHash;

  @Enumerated(EnumType.STRING)
  private ProfileStatus status;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ProfileFactEntity> facts = new ArrayList<>();

  protected ProfileVersionEntity() {}

  static ProfileVersionEntity from(ProfileVersion v, Instant now) {
    ProfileVersionEntity e = new ProfileVersionEntity();
    e.id = v.id();
    e.sourceFile = v.sourceFile();
    e.sourceHash = v.sourceHash();
    e.status = v.status();
    e.createdAt = now;
    e.updatedAt = now;
    v.facts().forEach(f -> e.facts.add(ProfileFactEntity.from(f, e, now)));
    return e;
  }

  @Override
  public String getId() {
    return id;
  }
}
