package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.service.LinkStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management endpoint for click analytics. */
@RestController
@RequestMapping("/api/links")
public class LinkStatsController {

  private final LinkStatsService linkStatsService;

  /** Creates a controller backed by the analytics service. */
  public LinkStatsController(LinkStatsService linkStatsService) {
    this.linkStatsService = linkStatsService;
  }

  @GetMapping("/{code}/stats")
  ResponseEntity<LinkStatsResponse> stats(@PathVariable String code) {
    return ResponseEntity.ok(LinkStatsResponse.from(linkStatsService.stats(code)));
  }
}
