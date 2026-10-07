package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.service.ShortLinkService;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

  private final ShortLinkService service;

  public RedirectController(ShortLinkService service) {
    this.service = service;
  }

  /**
   * 302 + no-store rather than 301: browsers cache a 301 and stop calling us, so later visits would
   * not be counted and an expired link would keep working from cache. The pattern only matches
   * valid codes, so other paths fall through to a normal 404.
   */
  @GetMapping("/{code:[0-9A-Za-z]{1,8}}")
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    String url = service.visit(code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(url))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
