package dev.curriculotron.sources;

import java.util.List;

public interface ConfiguredSourceRepository {
  record Board(String company, String adapter, String juniorJustification) {}

  List<Board> boards();
}
