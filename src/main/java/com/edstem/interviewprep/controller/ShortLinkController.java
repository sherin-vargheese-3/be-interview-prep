package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.ShortLinkResponse;
import com.edstem.interviewprep.dto.ShortenRequest;
import com.edstem.interviewprep.dto.ShortenResult;
import com.edstem.interviewprep.dto.StatsResponse;
import com.edstem.interviewprep.service.ShortLinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/links")
public class ShortLinkController {

  private final ShortLinkService service;

  public ShortLinkController(ShortLinkService service) {
    this.service = service;
  }

  /** 201 for a new link, 200 when an identical live link already existed. */
  @PostMapping
  public ResponseEntity<ShortLinkResponse> shorten(@Valid @RequestBody ShortenRequest request) {
    ShortenResult result = service.shorten(request.url().strip(), request.expiresAt());
    URI shortUrl =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/{code}")
            .buildAndExpand(result.link().code())
            .toUri();
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(shortUrl)
        .body(result.link().withShortUrl(shortUrl.toString()));
  }

  @GetMapping("/{code}/stats")
  public StatsResponse stats(@PathVariable String code) {
    return service.stats(code);
  }
}
