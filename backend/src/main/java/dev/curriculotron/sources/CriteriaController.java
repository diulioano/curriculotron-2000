package dev.curriculotron.sources;

import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.shared.web.ApiError;
import dev.curriculotron.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/criteria")
public final class CriteriaController {
  private final CriteriaService criteria;

  public CriteriaController(CriteriaService criteria) {
    this.criteria = criteria;
  }

  public record Request(
      @Positive long version,
      @NotNull @Size(max = 100) List<@NotNull @Size(max = 200) String> targetRoles,
      @NotNull @Size(max = 100) List<@NotNull @Size(max = 200) String> stackKeywords,
      @NotNull Boolean remotePreferred,
      @NotNull @Size(max = 100) List<@NotNull @Size(max = 200) String> hybridRegions,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
          BigDecimal cltMinMonthlyBrl,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
          BigDecimal pjMinMonthlyBrl,
      @NotNull Boolean acceptJuniorIfJustified,
      @NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String dailySearchTimeLocal,
      @Min(1) @Max(365) int deadlineHighlightDays,
      @Min(1) @Max(8760) int urgentHours,
      @NotNull Boolean emailDigestEnabled) {
    CriteriaDocument document() {
      return new CriteriaDocument(
          version,
          targetRoles,
          stackKeywords,
          remotePreferred,
          hybridRegions,
          cltMinMonthlyBrl,
          pjMinMonthlyBrl,
          acceptJuniorIfJustified,
          dailySearchTimeLocal,
          deadlineHighlightDays,
          urgentHours,
          emailDigestEnabled);
    }
  }

  public record Response(
      long version,
      List<String> targetRoles,
      List<String> stackKeywords,
      boolean remotePreferred,
      List<String> hybridRegions,
      BigDecimal cltMinMonthlyBrl,
      BigDecimal pjMinMonthlyBrl,
      boolean acceptJuniorIfJustified,
      String dailySearchTimeLocal,
      int deadlineHighlightDays,
      int urgentHours,
      boolean emailDigestEnabled,
      CompatibilityStatus assessment) {}

  @GetMapping
  public Response get() {
    return response(criteria.view());
  }

  @PutMapping
  public Response put(@Valid @RequestBody Request request) {
    try {
      return response(criteria.saveView(request.document()));
    } catch (CriteriaVersionConflictException exception) {
      throw ApiException.conflict(
          "criteria_version_conflict", "Os critérios foram alterados. Recarregue antes de salvar.");
    } catch (IllegalArgumentException exception) {
      throw ApiException.badRequest("invalid_criteria", "Critérios inválidos.");
    }
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<ApiError> invalid(Exception exception) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("invalid_criteria", "Critérios inválidos."));
  }

  private Response response(CriteriaService.View view) {
    var value = view.criteria();
    return new Response(
        value.version(),
        value.targetRoles(),
        value.stackKeywords(),
        value.remotePreferred(),
        value.hybridRegions(),
        value.cltMinMonthlyBrl(),
        value.pjMinMonthlyBrl(),
        value.acceptJuniorIfJustified(),
        value.dailySearchTimeLocal(),
        value.deadlineHighlightDays(),
        value.urgentHours(),
        value.emailDigestEnabled(),
        view.assessment());
  }
}
