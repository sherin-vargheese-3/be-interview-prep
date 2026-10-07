package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.interviewprep.dto.ShortenResult;
import com.edstem.interviewprep.exception.LinkExpiredException;
import com.edstem.interviewprep.exception.LinkNotFoundException;
import com.edstem.interviewprep.model.ShortLink;
import com.edstem.interviewprep.repository.ShortLinkRepository;
import com.edstem.interviewprep.support.IntegrationTest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** Service-level tests against the real database. */
class ShortLinkServiceTest extends IntegrationTest {

  @Autowired ShortLinkService service;
  @Autowired ShortLinkRepository repository;
  @Autowired TransactionTemplate transactionTemplate;

  @Nested
  class Shorten {

    @Test
    void sameUrlAndExpiry_returnsExistingLink() {
      String url = uniqueUrl();
      ShortenResult first = service.shorten(url, null);

      ShortenResult second = service.shorten(url, null);

      assertThat(first.created()).isTrue();
      assertThat(second.created()).isFalse();
      assertThat(second.link().code()).isEqualTo(first.link().code());
    }

    @Test
    void differentExpiry_createsNewLink() {
      String url = uniqueUrl();
      ShortenResult permanent = service.shorten(url, null);

      ShortenResult expiring = service.shorten(url, NOW.plus(Duration.ofDays(1)));

      assertThat(expiring.created()).isTrue();
      assertThat(expiring.link().code()).isNotEqualTo(permanent.link().code());
    }

    @Test
    void existingLinkExpired_createsNewLink() {
      String url = uniqueUrl();
      Instant expiry = NOW.plus(Duration.ofHours(1));
      ShortenResult first = service.shorten(url, expiry);
      clock.advance(Duration.ofHours(1));

      ShortenResult second = service.shorten(url, expiry);

      assertThat(second.created()).isTrue();
      assertThat(second.link().code()).isNotEqualTo(first.link().code());
    }

    @Test
    void sameExpiryWithNanoseconds_stillReturnsExistingLink() {
      String url = uniqueUrl();
      Instant expiry = Instant.parse("2030-01-01T00:00:00.123456789Z");
      ShortenResult first = service.shorten(url, expiry);

      ShortenResult second = service.shorten(url, expiry);

      assertThat(second.created()).isFalse();
      assertThat(second.link().code()).isEqualTo(first.link().code());
    }

    @Test
    void savingATakenCode_failsInsteadOfOverwritingTheLink() {
      String code = service.shorten(uniqueUrl(), null).link().code();

      assertThatThrownBy(
              () -> repository.saveAndFlush(new ShortLink(code, "https://evil.example", null, NOW)))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void codeCollision_retriesWithNextCode() {
      // first link gets "aaaaaaa"; the second draws "aaaaaaa" again (taken), then "bbbbbbb"
      ShortLinkService scripted =
          new ShortLinkService(
              repository,
              new CodeGenerator(new ScriptedRandom(repeat(36, 14) + repeat(37, 7))),
              transactionTemplate,
              clock);

      String first = scripted.shorten(uniqueUrl(), null).link().code();
      String second = scripted.shorten(uniqueUrl(), null).link().code();

      assertThat(first).isEqualTo("aaaaaaa");
      assertThat(second).isEqualTo("bbbbbbb");
    }

    @Test
    void concurrentShortenOfSameUrl_yieldsSingleCode() throws Exception {
      String url = uniqueUrl();

      List<String> codes = runConcurrently(50, () -> service.shorten(url, null).link().code());

      assertThat(new HashSet<>(codes)).hasSize(1);
    }
  }

  @Nested
  class Visit {

    @Test
    void liveLink_returnsUrlAndCountsVisit() {
      String url = uniqueUrl();
      String code = service.shorten(url, null).link().code();

      assertThat(service.visit(code)).isEqualTo(url);
      assertThat(service.stats(code).visitCount()).isEqualTo(1);
    }

    @Test
    void unknownCode_throwsNotFound() {
      assertThatThrownBy(() -> service.visit("nope123"))
          .isInstanceOf(LinkNotFoundException.class)
          .hasMessage("Short code 'nope123' was not found");
    }

    @Test
    void expiredLink_throwsExpiredAndDoesNotCount() {
      String code = service.shorten(uniqueUrl(), NOW.plus(Duration.ofMinutes(5))).link().code();
      clock.advance(Duration.ofMinutes(5));

      assertThatThrownBy(() -> service.visit(code))
          .isInstanceOf(LinkExpiredException.class)
          .hasMessage("Short code '" + code + "' has expired");
      assertThat(service.stats(code).visitCount()).isZero();
    }

    @Test
    void manyConcurrentVisits_areAllCounted() throws Exception {
      String code = service.shorten(uniqueUrl(), null).link().code();
      int threads = 50;
      int visitsPerThread = 100;

      runConcurrently(
          threads,
          () -> {
            for (int i = 0; i < visitsPerThread; i++) {
              service.visit(code);
            }
            return null;
          });

      assertThat(service.stats(code).visitCount()).isEqualTo((long) threads * visitsPerThread);
    }
  }

  @Test
  void codeGenerator_producesUrlSafeCodesOfAtMost8Chars() {
    CodeGenerator generator = new CodeGenerator(new SecureRandom());
    Set<String> codes = new HashSet<>();

    for (int i = 0; i < 10_000; i++) {
      codes.add(generator.next());
    }

    assertThat(codes).hasSize(10_000).allMatch(code -> code.matches("[0-9A-Za-z]{1,8}"));
  }

  private static String uniqueUrl() {
    return "https://example.com/" + UUID.randomUUID();
  }

  private static String repeat(int alphabetIndex, int times) {
    return (alphabetIndex + ",").repeat(times);
  }

  /** Starts all tasks at the same instant (latch) to maximise contention, then waits for them. */
  private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  /** Feeds comma-separated alphabet indexes, so a code's characters can be scripted. */
  private static final class ScriptedRandom implements RandomGenerator {
    private final Deque<Integer> values = new ArrayDeque<>();

    ScriptedRandom(String indexes) {
      for (String index : indexes.split(",")) {
        values.add(Integer.parseInt(index));
      }
    }

    @Override
    public int nextInt(int bound) {
      return values.pop();
    }

    @Override
    public long nextLong() {
      throw new UnsupportedOperationException();
    }
  }
}
