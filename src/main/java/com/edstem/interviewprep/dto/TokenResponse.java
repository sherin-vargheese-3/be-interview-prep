package com.edstem.interviewprep.dto;

import java.time.Instant;

public record TokenResponse(
    String accessToken, String tokenType, long expiresIn, Instant expiresAt) {}
