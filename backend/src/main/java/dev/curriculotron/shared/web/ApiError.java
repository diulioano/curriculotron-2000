package dev.curriculotron.shared.web;

/** Corpo de erro padrão da API: {@code {"error":{"code":"…","message":"…"}}}. */
public record ApiError(ApiErrorBody error) {

  public static ApiError of(String code, String message) {
    return new ApiError(new ApiErrorBody(code, message));
  }
}
