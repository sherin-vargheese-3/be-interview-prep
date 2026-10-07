package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.model.ShortLink;
import java.time.Instant;

public record StatsResponse(
    String code, String url, long visitCount, Instant createdAt, Instant expiresAt) {

  public static StatsResponse from(ShortLink link) {
    return new StatsResponse(
        link.getCode(),
        link.getUrl(),
        link.getVisitCount(),
        link.getCreatedAt(),
        link.getExpiresAt());
  }
}
