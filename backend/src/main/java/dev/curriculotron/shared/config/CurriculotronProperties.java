package dev.curriculotron.shared.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração própria do app (application.yml, prefixo {@code curriculotron}). */
@ConfigurationProperties("curriculotron")
public record CurriculotronProperties(
    Path homeDir, Path dataDir, TlsProperties tls, String resumeFile, LoginProperties login) {

  public Path databaseFile() {
    return dataDir.resolve("curriculotron.db");
  }
}
