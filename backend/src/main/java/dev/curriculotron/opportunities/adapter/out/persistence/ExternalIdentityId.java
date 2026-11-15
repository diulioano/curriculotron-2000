package dev.curriculotron.opportunities.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

public class ExternalIdentityId implements Serializable {
  public String source;
  public String externalId;

  public ExternalIdentityId() {}

  public ExternalIdentityId(String source, String externalId) {
    this.source = source;
    this.externalId = externalId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ExternalIdentityId id)) return false;
    return Objects.equals(source, id.source) && Objects.equals(externalId, id.externalId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, externalId);
  }
}
