package dev.curriculotron.opportunities.application.port.out;

import java.util.List;

public interface MatchCandidateRepository {
  record Candidate(String id, String opportunityA, String opportunityB, String signal) {}

  List<Candidate> open();

  boolean reject(String a, String b, String at);

  boolean merge(String a, String b, String at);
}
