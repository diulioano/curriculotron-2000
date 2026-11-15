package dev.curriculotron.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/health}: público, sem dados. */
@RestController
class HealthController {

  public record HealthResponse(String status) {}

  @GetMapping("/api/health")
  HealthResponse health() {
    return new HealthResponse("ok");
  }
}
