package dev.curriculotron.salary.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

public class SalaryRefreshRequestId implements Serializable {
  public String opportunityId;
  public String requestKey;

  public SalaryRefreshRequestId() {}

  public SalaryRefreshRequestId(String opportunityId, String requestKey) {
    this.opportunityId = opportunityId;
    this.requestKey = requestKey;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof SalaryRefreshRequestId id)) return false;
    return Objects.equals(opportunityId, id.opportunityId)
        && Objects.equals(requestKey, id.requestKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(opportunityId, requestKey);
  }
}
