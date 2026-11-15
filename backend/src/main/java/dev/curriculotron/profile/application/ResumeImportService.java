package dev.curriculotron.profile.application;

import dev.curriculotron.audit.application.ActivityLogService;
import dev.curriculotron.audit.domain.ActivityEntry;
import dev.curriculotron.audit.domain.ActivityResult;
import dev.curriculotron.profile.application.port.out.ProfileVersionRepository;
import dev.curriculotron.profile.domain.FactOrigin;
import dev.curriculotron.profile.domain.ImportResult;
import dev.curriculotron.profile.domain.ParsedFact;
import dev.curriculotron.profile.domain.ProfileFact;
import dev.curriculotron.profile.domain.ProfileStatus;
import dev.curriculotron.profile.domain.ProfileVersion;
import dev.curriculotron.profile.domain.ResumeParser;
import dev.curriculotron.shared.Ids;
import dev.curriculotron.shared.persistence.WriteExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Importa o currículo (FR-001) como {@code ProfileVersion(status=imported)} e fatos {@code
 * origin=documented} com {@code origin_ref="<arquivo>:linha N"}. Um arquivo com o mesmo hash já
 * importado não gera nova versão.
 */
@Service
public class ResumeImportService {

  private final ProfileVersionRepository versions;
  private final WriteExecutor writer;
  private final ActivityLogService activity;

  public ResumeImportService(
      ProfileVersionRepository versions, WriteExecutor writer, ActivityLogService activity) {
    this.versions = versions;
    this.writer = writer;
    this.activity = activity;
  }

  public ImportResult importFile(Path file) throws IOException {
    String content = Files.readString(file, StandardCharsets.UTF_8);
    return importContent(file.getFileName().toString(), content);
  }

  public ImportResult importContent(String sourceFile, String content) {
    String hash = ResumeParser.sha256(content);
    List<ParsedFact> parsed = ResumeParser.parse(content);
    return writer.write(() -> {
      Optional<String> existing = versions.findIdBySourceHash(hash);
      if (existing.isPresent()) {
        return new ImportResult(existing.get(), false, 0);
      }
      List<ProfileFact> facts =
          parsed.stream()
              .map(f ->
                  new ProfileFact(
                      Ids.newId(),
                      f.kind(),
                      f.statement(),
                      FactOrigin.documented,
                      sourceFile + ":linha " + f.line()))
              .toList();
      var version =
          new ProfileVersion(Ids.newId(), sourceFile, hash, ProfileStatus.imported, facts);
      versions.save(version);
      activity.record(
          new ActivityEntry(
              null,
              "system",
              "profile.import",
              "profile_version",
              version.id(),
              sourceFile,
              ActivityResult.ok,
              null));
      return new ImportResult(version.id(), true, facts.size());
    });
  }
}
