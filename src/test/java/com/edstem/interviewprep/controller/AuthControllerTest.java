package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.model.User;
import com.edstem.interviewprep.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class AuthControllerTest extends AbstractAuthApiTest {

  @Autowired UserRepository userRepository;
  @Autowired PasswordEncoder passwordEncoder;

  @Nested
  class Register {

    @Test
    void validRequest_returns201AsUserWithoutPassword() throws Exception {
      String email = uniqueEmail();

      register(email, PASSWORD, "Ada")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.role").value("USER"))
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void password_isStoredAsBcryptHashNotPlainText() throws Exception {
      String email = uniqueEmail();

      register(email, PASSWORD, "Ada").andExpect(status().isCreated());

      User stored = userRepository.findByEmail(email).orElseThrow();
      assertThat(stored.getPasswordHash()).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
      assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    }

    @Test
    void roleInRequest_isRejectedSoClientsCannotSelfPromote() throws Exception {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "%s", "password": "%s", "name": "Eve", "role": "ADMIN"}
                      """
                          .formatted(uniqueEmail(), PASSWORD)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("role"));
    }

    @Test
    void duplicateEmail_caseInsensitive_returns409() throws Exception {
      String email = uniqueEmail();
      register(email, PASSWORD, "Ada").andExpect(status().isCreated());

      register(email.toUpperCase(), PASSWORD, "Ada again")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidFields_return400WithMessagePerField() throws Exception {
      register("not-an-email", "short", " ")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors", hasSize(3)))
          .andExpect(jsonPath("$.errors[0].field").value("email"))
          .andExpect(jsonPath("$.errors[0].message").value("email must be a valid email address"))
          .andExpect(jsonPath("$.errors[1].field").value("name"))
          .andExpect(jsonPath("$.errors[2].field").value("password"))
          .andExpect(
              jsonPath("$.errors[2].message")
                  .value("password must be between 8 and 72 characters"));
    }
  }

  @Nested
  class Login {

    @Test
    void validCredentials_returnBearerTokenValidFor15Minutes() throws Exception {
      String email = uniqueEmail();
      register(email, PASSWORD, "Ada");

      login(email, PASSWORD)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accessToken").isNotEmpty())
          .andExpect(jsonPath("$.tokenType").value("Bearer"))
          .andExpect(jsonPath("$.expiresIn").value(900))
          .andExpect(jsonPath("$.expiresAt").value("2026-10-07T10:15:00Z"))
          .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void wrongPasswordAndUnknownEmail_returnSame401() throws Exception {
      String email = uniqueEmail();
      register(email, PASSWORD, "Ada");

      for (var attempt : new String[][] {{email, "wrong-password"}, {uniqueEmail(), PASSWORD}}) {
        login(attempt[0], attempt[1])
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.detail").value("Invalid email or password"));
      }
    }
  }
}
