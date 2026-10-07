package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.ShortLinkResponse;
import com.edstem.interviewprep.dto.ShortenResult;
import com.edstem.interviewprep.dto.StatsResponse;
import com.edstem.interviewprep.exception.LinkExpiredException;
import com.edstem.interviewprep.exception.LinkNotFoundException;
import com.edstem.interviewprep.model.ShortLink;
import com.edstem.interviewprep.repository.ShortLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ShortLinkService {

  private static final int MAX_CODE_ATTEMPTS = 10;

  private final ShortLinkRepository repository;
  private final CodeGenerator codeGenerator;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public ShortLinkService(
      ShortLinkRepository repository,
      CodeGenerator codeGenerator,
      TransactionTemplate transaction,
      Clock clock) {
    this.repository = repository;
    this.codeGenerator = codeGenerator;
    this.transaction = transaction;
    this.clock = clock;
  }

  /**
   * Same URL + same expiry while that link is still live returns the existing code, so retries and
   * double-submits don't pile up duplicate codes. A different expiry, or an expired link, gets a
   * new code.
   *
   * <p>The lock is held around the whole transaction (TransactionTemplate inside the synchronized
   * method). With {@code @Transactional} on a synchronized method, the lock would be released
   * before the commit and a second request could miss the first one's uncommitted row. This covers
   * one instance; several instances would need a unique key in the database instead.
   */
  public synchronized ShortenResult shorten(String url, Instant expiresAt) {
    return transaction.execute(tx -> findOrCreate(url, expiresAt));
  }

  /** Resolves a code for redirection and counts the visit atomically in the database. */
  @Transactional
  public String visit(String code) {
    ShortLink link = repository.findById(code).orElseThrow(() -> new LinkNotFoundException(code));
    if (link.isExpiredAt(Instant.now(clock))) {
      throw new LinkExpiredException(code);
    }
    repository.incrementVisits(code);
    return link.getUrl();
  }

  /** Stats stay readable after expiry, so owners can still see how a link performed. */
  @Transactional(readOnly = true)
  public StatsResponse stats(String code) {
    return repository
        .findById(code)
        .map(StatsResponse::from)
        .orElseThrow(() -> new LinkNotFoundException(code));
  }

  private ShortenResult findOrCreate(String url, Instant expiresAt) {
    Instant now = Instant.now(clock);
    Optional<ShortLink> existing = repository.findLive(url, expiresAt, now).stream().findFirst();
    if (existing.isPresent()) {
      return new ShortenResult(ShortLinkResponse.from(existing.get()), false);
    }
    for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      String code = codeGenerator.next();
      if (!repository.existsById(code)) {
        ShortLink link = repository.save(new ShortLink(code, url, expiresAt, now));
        return new ShortenResult(ShortLinkResponse.from(link), true);
      }
    }
    throw new IllegalStateException(
        "Could not generate a unique short code after " + MAX_CODE_ATTEMPTS + " attempts");
  }
}
