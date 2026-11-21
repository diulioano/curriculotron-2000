package dev.curriculotron.opportunities.domain;

/**
 * Campos da vaga que a pessoa pode corrigir; o nome da constante é o valor persistido e exposto.
 */
public enum CorrectableOpportunityField {
  title,
  company_name,
  location,
  work_mode,
  contract_type,
  salary_min,
  salary_max,
  salary_unit,
  availability,
  deadline_at,
  published_at;

  public String wireValue() {
    return name();
  }
}
