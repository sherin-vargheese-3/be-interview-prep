package com.edstem.interviewprep.dto;

/** {@code created} is false when an existing live link for the same URL and expiry was reused. */
public record ShortenResult(ShortLinkResponse link, boolean created) {}
