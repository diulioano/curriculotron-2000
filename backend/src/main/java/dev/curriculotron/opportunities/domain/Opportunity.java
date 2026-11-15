package dev.curriculotron.opportunities.domain;

import java.math.BigDecimal;
import java.util.List;

public record Opportunity(
    String id,
    String title,
    String companyName,
    String location,
    String workMode,
    String contractType,
    Salary salary,
    List<String> facts,
    List<String> inferred,
    List<String> fitReasons,
    String userDecision,
    String availability,
    String mergedInto) {
  public record Salary(BigDecimal min, BigDecimal max, String unit) {}
}
