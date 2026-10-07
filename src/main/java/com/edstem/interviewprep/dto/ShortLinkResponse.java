package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.model.ShortLink;
import java.time.Instant;

/** {@code shortUrl} is filled in by the controller, which knows the request's host. */
public record ShortLinkResponse(
    String code, String shortUrl, String url, Instant expiresAt, Instant createdAt) {

  public static ShortLinkResponse from(ShortLink link) {
    return new ShortLinkResponse(
        link.getCode(), null, link.getUrl(), link.getExpiresAt(), link.getCreatedAt());
  }

  public ShortLinkResponse withShortUrl(String shortUrl) {
    return new ShortLinkResponse(code, shortUrl, url, expiresAt, createdAt);
  }
}
