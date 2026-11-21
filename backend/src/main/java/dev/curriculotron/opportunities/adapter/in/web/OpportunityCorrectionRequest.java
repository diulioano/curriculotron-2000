package dev.curriculotron.opportunities.adapter.in.web;

import dev.curriculotron.opportunities.application.OpportunityCorrectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Correção parcial de uma vaga. Campos omitidos não mudam. Para as datas, chave ausente mantém o
 * valor e {@code null} explícito limpa a data ({@link ClearableDate#CLEAR}).
 */
public record OpportunityCorrectionRequest(
    @Pattern(regexp = NOT_BLANK, message = "não pode ficar vazio") @Size(max = 240) String title,
    @Pattern(regexp = NOT_BLANK, message = "não pode ficar vazio") @Size(max = 160)
        String companyName,
    @Size(max = 160) String location,
    WorkMode workMode,
    ContractType contractType,
    @Valid SalaryRequest salary,
    Boolean clearSalary,
    Availability availability,
    ClearableDate deadlineAt,
    ClearableDate publishedAt,
    @Size(max = 500) String reason) {
  private static final String NOT_BLANK = "(?s).*\\S.*";

  public enum WorkMode {
    remote,
    hybrid,
    onsite,
    unknown
  }

  public enum ContractType {
    clt,
    pj,
    other,
    unknown
  }

  public enum Availability {
    open,
    closed,
    unconfirmed
  }

  public enum SalaryUnit {
    monthly,
    hourly,
    yearly,
    unknown
  }

  public record SalaryRequest(
      @DecimalMin("0") @Digits(integer = 9, fraction = 2) BigDecimal min,
      @DecimalMin("0") @Digits(integer = 9, fraction = 2) BigDecimal max,
      @NotNull SalaryUnit unit) {}

  public OpportunityCorrectionService.Command toCommand() {
    return new OpportunityCorrectionService.Command(
        title,
        companyName,
        location,
        workMode == null ? null : workMode.name(),
        contractType == null ? null : contractType.name(),
        salary == null
            ? null
            : new OpportunityCorrectionService.Salary(
                salary.min(), salary.max(), salary.unit().name()),
        Boolean.TRUE.equals(clearSalary),
        availability == null ? null : availability.name(),
        ClearableDate.toChange(deadlineAt),
        ClearableDate.toChange(publishedAt),
        reason);
  }
}
