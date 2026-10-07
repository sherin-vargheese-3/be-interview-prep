package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.model.ShortLink;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, String> {

  /** Live links for exactly this URL and expiry (a null expiry matches only "never expires"). */
  @Query(
      "select l from ShortLink l where l.url = :url"
          + " and ((:expiresAt is null and l.expiresAt is null) or l.expiresAt = :expiresAt)"
          + " and (l.expiresAt is null or l.expiresAt > :now)"
          + " order by l.createdAt desc")
  List<ShortLink> findLive(
      @Param("url") String url, @Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

  /**
   * One atomic statement: the database increments under a row lock, so concurrent visits are never
   * lost. Reading the count, adding one in Java and saving it back would lose updates.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update ShortLink l set l.visitCount = l.visitCount + 1 where l.code = :code")
  int incrementVisits(@Param("code") String code);
}
