package dev.curriculotron.shared.web;

/** Tipos de pendência exibidos em "Precisa de você"; o nome é o valor de transporte. */
public enum PendingReviewKind {
  dedupe_candidate,
  review_item,
  email_attention,
  missed_routine
}
