package dev.curriculotron.profile.adapter.in.startup;

import dev.curriculotron.profile.application.ResumeImportService;
import dev.curriculotron.shared.config.CurriculotronProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Importa o currículo no início quando {@code curriculotron.resume-file} está definido. Reimportar
 * o mesmo arquivo não cria nova versão (hash).
 */
@Component
class ResumeStartupImport implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ResumeStartupImport.class);

  private final ResumeImportService importer;
  private final CurriculotronProperties props;

  ResumeStartupImport(ResumeImportService importer, CurriculotronProperties props) {
    this.importer = importer;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (props.resumeFile() == null || props.resumeFile().isBlank()) {
      return;
    }
    Path file = Path.of(props.resumeFile());
    if (!Files.isRegularFile(file)) {
      log.warn("Currículo configurado não encontrado: {}", file.getFileName());
      return;
    }
    var result = importer.importFile(file);
    if (result.created()) {
      log.info("Currículo importado: {} fatos", result.factCount());
    }
  }
}
