package dev.curriculotron.opportunities;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class CompatibilityFixtures {
  private CompatibilityFixtures() {}

  public record Posting(
      String id,
      String title,
      String text,
      String completeness,
      List<String> roles,
      List<String> technologies,
      String expectedRole,
      String expectedTechnology) {
    public Posting {
      roles = List.copyOf(roles);
      technologies = List.copyOf(technologies);
    }
  }

  public static List<Posting> postings() {
    try (InputStream input =
        CompatibilityFixtures.class.getResourceAsStream("/compatibility/postings.json")) {
      if (input == null) throw new IllegalStateException("Compatibility fixtures not found");
      return List.copyOf(
          JsonMapper.builder().build().readValue(input, new TypeReference<List<Posting>>() {}));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
