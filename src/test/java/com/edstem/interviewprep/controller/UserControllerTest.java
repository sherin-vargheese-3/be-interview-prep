package com.edstem.interviewprep.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

class UserControllerTest extends AbstractAuthApiTest {

  private static final String ME = "/api/v1/users/me";
  private static final String ALL_USERS = "/api/v1/users";

  @Nested
  class AdminEndpoint {

    /** The acceptance criterion: a USER is authenticated but not authorised. */
    @Test
    void user_cannotListAllUsers_gets403Json() throws Exception {
      String token = userToken();

      getWithToken(ALL_USERS, token)
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.code").value("FORBIDDEN"))
          .andExpect(jsonPath("$.instance").value(ALL_USERS));
    }

    @Test
    void user_cannotReachAdminEndpointWithHead() throws Exception {
      String token = userToken();

      mockMvc
          .perform(head(ALL_USERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isForbidden());
    }

    @Test
    void admin_canListAllUsers() throws Exception {
      String email = uniqueEmail();
      register(email, PASSWORD, "Listed User");

      getWithToken(ALL_USERS, adminToken())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].email", hasItem(email)))
          .andExpect(jsonPath("$[*].email", hasItem(ADMIN_EMAIL)))
          .andExpect(jsonPath("$[*].passwordHash").doesNotExist());
    }

    @Test
    void anonymous_gets401NotForbidden() throws Exception {
      mockMvc
          .perform(get(ALL_USERS))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
  }

  @Nested
  class OwnProfile {

    @Test
    void user_seesOwnProfile() throws Exception {
      String email = uniqueEmail();
      register(email, PASSWORD, "Grace");

      getWithToken(ME, tokenFor(email, PASSWORD))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.name").value("Grace"))
          .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void admin_seesOwnProfile() throws Exception {
      getWithToken(ME, adminToken())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("ADMIN"));
    }
  }

  @Nested
  class Unauthenticated {

    @Test
    void noToken_returns401JsonNotHtml() throws Exception {
      mockMvc
          .perform(get(ME).accept(MediaType.TEXT_HTML))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenExpiresAfter15Minutes() throws Exception {
      String token = userToken();

      clock.advance(Duration.ofMinutes(15).minusSeconds(1));
      getWithToken(ME, token).andExpect(status().isOk());

      // Spring rejects once now is strictly after exp (exactly 15:00.000 is the last valid instant)
      clock.advance(Duration.ofSeconds(2));
      getWithToken(ME, token)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void tamperedToken_returns401() throws Exception {
      String token = userToken();
      int i = token.lastIndexOf('.') + 5;
      String tampered =
          token.substring(0, i) + (token.charAt(i) == 'A' ? 'B' : 'A') + token.substring(i + 1);

      getWithToken(ME, tampered)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void tokenSignedWithAnotherKeyClaimingAdmin_returns401() throws Exception {
      var otherKey =
          new SecretKeySpec(randomSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      JwtClaimsSet claims =
          JwtClaimsSet.builder()
              .issuer("be-interview-prep")
              .subject(UUID.randomUUID().toString())
              .issuedAt(NOW)
              .expiresAt(NOW.plus(Duration.ofMinutes(15)))
              .claim("roles", List.of("ADMIN"))
              .build();
      String forged =
          new NimbusJwtEncoder(new ImmutableSecret<>(otherKey))
              .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
              .getTokenValue();

      getWithToken(ALL_USERS, forged)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
  }

  private ResultActions getWithToken(String path, String token) throws Exception {
    return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }
}
