package dev.curriculotron.salary.application.port.out;

import dev.curriculotron.salary.domain.*;
import java.util.*;

public interface SalaryEstimateRepository {
  Optional<SalaryEstimate> latest(SalaryProfile profile);

  void append(SalaryEstimate estimate);

  void link(String opportunityId, SalaryEstimate estimate, String at);

  List<SalaryEstimate> all();
}
