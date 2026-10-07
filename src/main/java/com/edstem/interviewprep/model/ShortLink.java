package com.edstem.interviewprep.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A short code mapped to its original URL. {@code code} is the primary key, so the database itself
 * guarantees codes are unique. {@code visitCount} is only changed by an atomic SQL increment (see
 * {@code ShortLinkRepository#incrementVisits}), never read-modify-written in Java.
 */
@Entity
@Table(name = "short_links", indexes = @Index(name = "idx_short_links_url", columnList = "url"))
public class ShortLink {

  @Id
  @Column(length = 8)
  private String code;

  @Column(nullable = false, length = 2048)
  private String url;

  /** {@code null} means the link never expires. */
  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "visit_count", nullable = false)
  private long visitCount;

  protected ShortLink() {}

  public ShortLink(String code, String url, Instant expiresAt, Instant createdAt) {
    this.code = code;
    this.url = url;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public boolean isExpiredAt(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  public String getCode() {
    return code;
  }

  public String getUrl() {
    return url;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVisitCount() {
    return visitCount;
  }
}
