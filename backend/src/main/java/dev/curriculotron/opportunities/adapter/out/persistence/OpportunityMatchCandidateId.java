package dev.curriculotron.opportunities.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

public class OpportunityMatchCandidateId implements Serializable {
  public String opportunityA;
  public String opportunityB;

  public OpportunityMatchCandidateId() {}

  public OpportunityMatchCandidateId(String opportunityA, String opportunityB) {
    this.opportunityA = opportunityA;
    this.opportunityB = opportunityB;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof OpportunityMatchCandidateId id)) return false;
    return Objects.equals(opportunityA, id.opportunityA)
        && Objects.equals(opportunityB, id.opportunityB);
  }

  @Override
  public int hashCode() {
    return Objects.hash(opportunityA, opportunityB);
  }
}
