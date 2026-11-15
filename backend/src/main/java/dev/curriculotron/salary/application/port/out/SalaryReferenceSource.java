package dev.curriculotron.salary.application.port.out;

import dev.curriculotron.salary.domain.*;

public interface SalaryReferenceSource {
  record Result(String state, SalaryEstimate estimate) {}

  Result lookup(SalaryProfile profile);
}
