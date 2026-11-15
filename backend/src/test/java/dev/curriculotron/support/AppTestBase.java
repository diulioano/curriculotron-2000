package dev.curriculotron.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Contexto Spring com SQLite real em diretório temporário próprio da classe de teste, HTTPS e
 * agendador desligados.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
public abstract class AppTestBase {

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    Path home;
    try {
      home = Files.createTempDirectory("curriculotron-test-");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    registry.add("curriculotron.home-dir", home::toString);
    registry.add("server.ssl.enabled", () -> "false");
    registry.add("curriculotron.scheduler.enabled", () -> "false");
    registry.add("curriculotron.resume-file", () -> "");
  }
}
