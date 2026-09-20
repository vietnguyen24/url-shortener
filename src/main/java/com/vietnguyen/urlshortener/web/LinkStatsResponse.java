package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.service.LinkStats;
import java.time.LocalDate;
import java.util.Map;

/** HTTP response DTO for link analytics. */
record LinkStatsResponse(
    String shortCode,
    long totalClicks,
    Map<LocalDate, Long> clicksByDay,
    Map<String, Long> referrers,
    Map<String, Long> userAgents) {

  static LinkStatsResponse from(LinkStats stats) {
    return new LinkStatsResponse(
        stats.shortCode(),
        stats.totalClicks(),
        stats.clicksByDay(),
        stats.referrers(),
        stats.userAgents());
  }
}
