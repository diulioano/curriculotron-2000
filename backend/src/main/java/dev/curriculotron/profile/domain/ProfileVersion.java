package dev.curriculotron.profile.domain;

import java.util.List;

/** Versão do perfil gerada a partir de um arquivo de currículo, com seus fatos. */
public record ProfileVersion(
    String id,
    String sourceFile,
    String sourceHash,
    ProfileStatus status,
    List<ProfileFact> facts) {

  public ProfileVersion {
    facts = List.copyOf(facts);
  }
}
