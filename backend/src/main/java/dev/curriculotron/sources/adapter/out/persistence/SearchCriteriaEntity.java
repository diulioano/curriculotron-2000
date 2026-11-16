package dev.curriculotron.sources.adapter.out.persistence;

import dev.curriculotron.shared.persistence.StringListJsonConverter;
import dev.curriculotron.sources.CriteriaDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "search_criteria")
class SearchCriteriaEntity {
  @Id private Integer id;

  @Column(name = "version", nullable = false)
  private long version;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "target_roles", nullable = false)
  private List<String> targetRoles;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "stack_keywords", nullable = false)
  private List<String> stackKeywords;

  @Column(name = "remote_preferred", nullable = false)
  private Integer remotePreferred;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "hybrid_regions", nullable = false)
  private List<String> hybridRegions;

  @Column(name = "clt_min_monthly_brl", nullable = false)
  private BigDecimal cltMinimum;

  @Column(name = "pj_min_monthly_brl", nullable = false)
  private BigDecimal pjMinimum;

  @Column(name = "accept_junior_if_justified", nullable = false)
  private Integer acceptJuniorIfJustified;

  @Column(name = "daily_search_time_local", nullable = false)
  private String dailySearchTimeLocal;

  @Column(name = "deadline_highlight_days", nullable = false)
  private int deadlineHighlightDays;

  @Column(name = "urgent_hours", nullable = false)
  private int urgentHours;

  @Column(name = "email_digest_enabled", nullable = false)
  private int emailDigestEnabled;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  CriteriaDocument document() {
    return new CriteriaDocument(
        version,
        targetRoles,
        stackKeywords,
        remotePreferred(),
        hybridRegions,
        cltMinimum,
        pjMinimum,
        acceptJuniorIfJustified(),
        dailySearchTimeLocal,
        deadlineHighlightDays,
        urgentHours,
        emailDigestEnabled != 0);
  }

  void apply(CriteriaDocument value, String at) {
    version = value.version();
    targetRoles = value.targetRoles();
    stackKeywords = value.stackKeywords();
    remotePreferred = value.remotePreferred() ? 1 : 0;
    hybridRegions = value.hybridRegions();
    cltMinimum = value.cltMinMonthlyBrl();
    pjMinimum = value.pjMinMonthlyBrl();
    acceptJuniorIfJustified = value.acceptJuniorIfJustified() ? 1 : 0;
    dailySearchTimeLocal = value.dailySearchTimeLocal();
    deadlineHighlightDays = value.deadlineHighlightDays();
    urgentHours = value.urgentHours();
    emailDigestEnabled = value.emailDigestEnabled() ? 1 : 0;
    updatedAt = at;
  }

  protected SearchCriteriaEntity() {}

  List<String> targetRoles() {
    return targetRoles;
  }

  List<String> stackKeywords() {
    return stackKeywords;
  }

  List<String> hybridRegions() {
    return hybridRegions;
  }

  boolean remotePreferred() {
    return remotePreferred != null && remotePreferred != 0;
  }

  boolean acceptJuniorIfJustified() {
    return acceptJuniorIfJustified != null && acceptJuniorIfJustified != 0;
  }

  BigDecimal cltMinimum() {
    return cltMinimum;
  }

  BigDecimal pjMinimum() {
    return pjMinimum;
  }

  String dailySearchTimeLocal() {
    return dailySearchTimeLocal;
  }
}
