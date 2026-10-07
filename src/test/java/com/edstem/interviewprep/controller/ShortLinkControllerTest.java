package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.dto.ShortenRequest;
import com.edstem.interviewprep.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end through the real MVC stack, validation, error handler and database. */
class ShortLinkControllerTest extends IntegrationTest {

  private static final String LINKS = "/api/v1/links";

  @Autowired ObjectMapper objectMapper;

  @Nested
  class Shorten {

    @Test
    void validUrl_returns201WithCodeAndShortUrl() throws Exception {
      String url = uniqueUrl();

      shorten(url, null)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.code", matchesPattern("[0-9A-Za-z]{1,8}")))
          .andExpect(jsonPath("$.shortUrl", matchesPattern("http://localhost/[0-9A-Za-z]{1,8}")))
          .andExpect(jsonPath("$.url").value(url))
          .andExpect(jsonPath("$.createdAt").value("2026-10-07T10:00:00Z"))
          .andExpect(header().exists("Location"));
    }

    @Test
    void sameUrlTwice_returnsSameCodeWith200() throws Exception {
      String url = uniqueUrl();
      String first = codeOf(shorten(url, null).andExpect(status().isCreated()));

      String second = codeOf(shorten(url, null).andExpect(status().isOk()));

      assertThat(second).isEqualTo(first);
    }

    @Test
    void invalidUrls_return400WithFieldMessage() throws Exception {
      for (String bad : List.of("not a url", "ftp://example.com/file", "https://", "/relative")) {
        shorten(bad, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors", hasSize(1)))
            .andExpect(jsonPath("$.errors[0].field").value("url"))
            .andExpect(
                jsonPath("$.errors[0].message").value("url must be a valid http or https URL"));
      }
    }

    @Test
    void missingUrlAndPastExpiry_return400PerField() throws Exception {
      mockMvc
          .perform(
              post(LINKS)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expiresAt\": \"2026-10-07T09:00:00Z\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors", hasSize(2)))
          .andExpect(jsonPath("$.errors[0].field").value("expiresAt"))
          .andExpect(jsonPath("$.errors[0].message").value("expiresAt must be in the future"))
          .andExpect(jsonPath("$.errors[1].field").value("url"))
          .andExpect(jsonPath("$.errors[1].message").value("url is required"));
    }
  }

  @Nested
  class RedirectAndStats {

    @Test
    void visit_redirects302AndIsCountedInStats() throws Exception {
      String url = uniqueUrl();
      String code = codeOf(shorten(url, null));

      mockMvc
          .perform(get("/" + code))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", url))
          .andExpect(header().string("Cache-Control", "no-store"));
      mockMvc.perform(get("/" + code)).andExpect(status().isFound());

      mockMvc
          .perform(get(LINKS + "/" + code + "/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.url").value(url))
          .andExpect(jsonPath("$.visitCount").value(2))
          .andExpect(jsonPath("$.createdAt").value("2026-10-07T10:00:00Z"));
    }

    @Test
    void unknownCode_returns404() throws Exception {
      mockMvc
          .perform(get("/zzzzzzz"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
      mockMvc
          .perform(get(LINKS + "/zzzzzzz/stats"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }

    @Test
    void expiredCode_returns410ButStatsRemainAvailable() throws Exception {
      String code = codeOf(shorten(uniqueUrl(), "2026-10-07T11:00:00Z"));
      clock.advance(Duration.ofHours(1));

      mockMvc
          .perform(get("/" + code))
          .andExpect(status().isGone())
          .andExpect(jsonPath("$.status").value(410))
          .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
      mockMvc
          .perform(get(LINKS + "/" + code + "/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.visitCount").value(0));
    }

    @Test
    void concurrentVisitsOverHttp_areAllCounted() throws Exception {
      String code = codeOf(shorten(uniqueUrl(), null));
      int threads = 16;
      int visitsPerThread = 50;
      CountDownLatch start = new CountDownLatch(1);

      ExecutorService pool = Executors.newFixedThreadPool(threads);
      try {
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
          futures.add(
              pool.submit(
                  () -> {
                    start.await();
                    for (int i = 0; i < visitsPerThread; i++) {
                      mockMvc.perform(get("/" + code)).andExpect(status().isFound());
                    }
                    return null;
                  }));
        }
        start.countDown();
        for (Future<?> future : futures) {
          future.get();
        }
      } finally {
        pool.shutdownNow();
      }

      mockMvc
          .perform(get(LINKS + "/" + code + "/stats"))
          .andExpect(jsonPath("$.visitCount").value(threads * visitsPerThread));
    }
  }

  private ResultActions shorten(String url, String expiresAt) throws Exception {
    String body =
        expiresAt == null
            ? objectMapper.writeValueAsString(new ShortenRequest(url, null))
            : "{\"url\": \"%s\", \"expiresAt\": \"%s\"}".formatted(url, expiresAt);
    return mockMvc.perform(post(LINKS).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String codeOf(ResultActions result) throws Exception {
    return objectMapper
        .readTree(result.andReturn().getResponse().getContentAsString())
        .get("code")
        .asText();
  }

  /** Each test shortens its own URL so tests sharing the app context don't affect each other. */
  private static String uniqueUrl() {
    return "https://example.com/articles/" + UUID.randomUUID();
  }
}
