package dev.curriculotron.profile.adapter.in.web;

import dev.curriculotron.opportunities.application.CompatibilityStatus;
import dev.curriculotron.profile.application.CareerProfileService;
import dev.curriculotron.profile.application.ProfileVersionConflictException;
import dev.curriculotron.profile.domain.CareerProfile;
import dev.curriculotron.profile.domain.FieldProvenance;
import dev.curriculotron.profile.domain.InvalidProfileException;
import dev.curriculotron.profile.domain.InvalidResumeDocumentException;
import dev.curriculotron.profile.domain.ProfileDraft;
import dev.curriculotron.profile.domain.Seniority;
import dev.curriculotron.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Perfil de carreira (US006), contrato em {@code specs/006-perfil-descoberta-estudo/api.md}. */
@RestController
@RequestMapping("/api/profile")
public final class ProfileController {
  private static final String PREVIEW_PATH = "/api/profile/import/preview";

  private final CareerProfileService profiles;
  private final ResumeDocumentExtractor extractor;

  public ProfileController(CareerProfileService profiles, ResumeDocumentExtractor extractor) {
    this.profiles = profiles;
    this.extractor = extractor;
  }

  public record PreviewRequest(@NotBlank @Size(max = 100_000) String markdown) {}

  /** {@code provenance} é opcional: clientes sem metadados salvam sem origem por campo. */
  public record SaveRequest(
      @PositiveOrZero long version,
      @NotBlank @Size(max = 200) String role,
      @NotBlank @Size(max = 100) String primaryTechnology,
      @NotNull @Size(max = 30) List<@NotNull @Size(max = 100) String> secondaryTechnologies,
      @NotNull @Size(max = 30) List<@NotNull @Size(max = 100) String> flexibleTechnologies,
      @NotNull Seniority currentSeniority,
      @NotEmpty @Size(max = 3) List<@NotNull Seniority> targetLevels,
      @NotNull @Size(max = 20) List<@NotNull @Size(max = 200) String> priorities,
      @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
          BigDecimal juniorFloorCltMonthlyBrl,
      @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
          BigDecimal juniorFloorPjMonthlyBrl,
      @NotNull CareerProfile.Origin origin,
      Map<ProfileDraft.Field, FieldProvenance> provenance) {
    CareerProfile profile() {
      return new CareerProfile(
          1,
          role,
          primaryTechnology,
          secondaryTechnologies,
          flexibleTechnologies,
          currentSeniority,
          targetLevels,
          priorities,
          juniorFloorCltMonthlyBrl,
          juniorFloorPjMonthlyBrl,
          origin,
          provenance);
    }
  }

  public record ProfileResponse(
      long version,
      String role,
      String primaryTechnology,
      List<String> secondaryTechnologies,
      List<String> flexibleTechnologies,
      Seniority currentSeniority,
      List<Seniority> targetLevels,
      List<String> priorities,
      BigDecimal juniorFloorCltMonthlyBrl,
      BigDecimal juniorFloorPjMonthlyBrl,
      CareerProfile.Origin origin,
      Map<ProfileDraft.Field, FieldProvenance> provenance,
      String updatedAt) {}

  /** {@code profile} é {@code null} enquanto nada foi salvo. */
  public record ViewResponse(
      ProfileResponse profile, CompatibilityStatus assessment, List<String> priorityCompanies) {}

  @GetMapping
  public ViewResponse get() {
    return response(profiles.view());
  }

  @PostMapping(value = "/import/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ProfileDraft.Preview preview(@Valid @RequestBody PreviewRequest request) {
    return profiles.preview(request.markdown());
  }

  @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ProfileDraft.Preview previewFile(@RequestPart("file") MultipartFile file) {
    try {
      String text =
          extractor.extract(file.getBytes(), file.getOriginalFilename(), file.getContentType());
      return profiles.preview(text);
    } catch (IOException e) {
      throw new InvalidResumeDocumentException("Não foi possível ler o arquivo enviado.", e);
    }
  }

  @PutMapping
  public ViewResponse put(@Valid @RequestBody SaveRequest request) {
    return response(profiles.save(request.version(), request.profile()));
  }

  @ExceptionHandler(ProfileVersionConflictException.class)
  ResponseEntity<ApiError> conflict() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiError.of(
                "profile_version_conflict", "O perfil foi alterado. Recarregue antes de salvar."));
  }

  @ExceptionHandler(InvalidResumeDocumentException.class)
  ResponseEntity<ApiError> invalidDocument(InvalidResumeDocumentException error) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("invalid_profile_document", error.getMessage()));
  }

  @ExceptionHandler({
    InvalidProfileException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestPartException.class
  })
  ResponseEntity<ApiError> invalid(HttpServletRequest request) {
    if (PREVIEW_PATH.equals(request.getRequestURI()))
      return ResponseEntity.badRequest()
          .body(ApiError.of("invalid_profile_markdown", "Conteúdo do currículo inválido."));
    return ResponseEntity.badRequest().body(ApiError.of("invalid_profile", "Perfil inválido."));
  }

  private static ViewResponse response(CareerProfileService.View view) {
    var stored = view.profile();
    if (stored == null) return new ViewResponse(null, view.assessment(), view.priorityCompanies());
    var value = stored.profile();
    return new ViewResponse(
        new ProfileResponse(
            value.version(),
            value.role(),
            value.primaryTechnology(),
            value.secondaryTechnologies(),
            value.flexibleTechnologies(),
            value.currentSeniority(),
            value.targetLevels(),
            value.priorities(),
            value.juniorFloorCltMonthlyBrl(),
            value.juniorFloorPjMonthlyBrl(),
            value.origin(),
            value.provenance(),
            stored.updatedAt().toString()),
        view.assessment(),
        view.priorityCompanies());
  }
}
