package dev.curriculotron.profile.domain;

/** Fato extraído do Markdown, com a linha de origem (1-based). */
public record ParsedFact(FactKind kind, String statement, int line) {}
