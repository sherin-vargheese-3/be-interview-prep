package com.edstem.interviewprep.service;

import com.edstem.interviewprep.config.JwtProperties;
import com.edstem.interviewprep.config.SecurityConfig;
import com.edstem.interviewprep.dto.TokenResponse;
import com.edstem.interviewprep.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens that expire after {@code app.jwt.ttl} (15 minutes). */
@Service
public class TokenService {

  private final JwtEncoder encoder;
  private final JwtProperties properties;
  private final Clock clock;

  public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
  }

  public TokenResponse issue(User user) {
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(properties.ttl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(SecurityConfig.ROLES_CLAIM, List.of(user.getRole().name()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new TokenResponse(token, "Bearer", properties.ttl().toSeconds(), expiresAt);
  }
}
