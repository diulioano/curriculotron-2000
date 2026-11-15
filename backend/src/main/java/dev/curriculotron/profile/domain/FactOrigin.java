package dev.curriculotron.profile.domain;

/**
 * De onde vem um fato. Invariante (FR-020, RB-006): {@code study_recommended} nunca é evidência de
 * competência.
 */
public enum FactOrigin {
  documented,
  declared,
  study_recommended
}
