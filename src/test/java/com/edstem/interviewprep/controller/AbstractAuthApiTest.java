package com.edstem.interviewprep.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers to register users and obtain real tokens through the login endpoint. */
abstract class AbstractAuthApiTest extends IntegrationTest {

  static final String PASSWORD = "correct-horse-battery";

  @Autowired ObjectMapper objectMapper;

  ResultActions register(String email, String password, String name) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of("email", email, "password", password, "name", name))));
  }

  ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
  }

  /** Registers a fresh USER and returns their access token. */
  String userToken() throws Exception {
    String email = uniqueEmail();
    register(email, PASSWORD, "Test User").andExpect(status().isCreated());
    return tokenFor(email, PASSWORD);
  }

  String adminToken() throws Exception {
    return tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  String tokenFor(String email, String password) throws Exception {
    String body =
        login(email, password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("accessToken").asText();
  }

  static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  static String randomSecret() {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
