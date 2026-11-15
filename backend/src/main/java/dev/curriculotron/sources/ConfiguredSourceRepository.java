package dev.curriculotron.sources;

import java.util.List;
import java.util.Optional;

public interface ConfiguredSourceRepository {
  record Board(String company, String adapter, String juniorJustification) {}

  List<Board> boards();

  /** User's reason to accept junior roles at this company, whether or not it has a career page. */
  Optional<String> juniorJustification(String company);
}
