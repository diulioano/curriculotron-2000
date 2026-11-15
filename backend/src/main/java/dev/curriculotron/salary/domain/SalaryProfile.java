package dev.curriculotron.salary.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

public record SalaryProfile(
    String company,
    String role,
    String seniority,
    String location,
    String workMode,
    String contractType) {
  public SalaryProfile {
    company = normalize(company);
    role = normalize(role);
    seniority = normalize(seniority);
    location = normalize(location);
    workMode = normalize(workMode);
    contractType = normalize(contractType);
  }

  public static String normalize(String s) {
    return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .trim()
        .replaceAll("\\s+", " ");
  }

  public boolean complete() {
    return !role.isBlank()
        && !seniority.isBlank()
        && !location.isBlank()
        && Set.of("clt", "pj").contains(contractType);
  }

  public SalaryProfile market() {
    return new SalaryProfile("", role, seniority, location, workMode, contractType);
  }

  public String key() {
    try {
      // Length-prefixed values avoid delimiter collisions in user-supplied names.
      String raw = "v1";
      for (String s : List.of(company, role, seniority, location, workMode, contractType))
        raw += ":" + s.length() + ":" + s;
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
