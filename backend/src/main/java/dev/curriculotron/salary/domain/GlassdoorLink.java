package dev.curriculotron.salary.domain;

import java.net.*;
import java.util.Set;

public final class GlassdoorLink {
  private GlassdoorLink() {}

  public static String validate(String url) {
    try {
      var uri = URI.create(url);
      if (!"https".equals(uri.getScheme())
          || !Set.of("www.glassdoor.com.br", "www.glassdoor.com").contains(uri.getHost())
          || uri.getUserInfo() != null
          || uri.getPort() != -1) throw new IllegalArgumentException("URL Glassdoor inválida.");
      return uri.toASCIIString();
    } catch (Exception e) {
      throw new IllegalArgumentException("URL Glassdoor inválida.");
    }
  }

  public static String search(String role) {
    String slug = SalaryProfile.normalize(role).replace(' ', '-');
    return "https://www.glassdoor.com.br/Sal%C3%A1rios/"
        + URLEncoder.encode(slug, java.nio.charset.StandardCharsets.UTF_8)
        + "-sal%C3%A1rio-SRCH_KO0,"
        + role.length()
        + ".htm";
  }
}
