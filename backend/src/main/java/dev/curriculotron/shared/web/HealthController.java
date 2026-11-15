package dev.curriculotron.shared.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/health}: público, sem dados. */
@RestController
class HealthController {

  @GetMapping("/api/health")
  Map<String, String> health() {
    return Map.of("status", "ok");
  }
}
