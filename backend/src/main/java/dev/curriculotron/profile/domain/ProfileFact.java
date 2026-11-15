package dev.curriculotron.profile.domain;

/**
 * Fato atômico do perfil, com origem rastreável ({@code originRef}, ex.: {@code cv.md:linha 12}).
 */
public record ProfileFact(
    String id, FactKind kind, String statement, FactOrigin origin, String originRef) {}
