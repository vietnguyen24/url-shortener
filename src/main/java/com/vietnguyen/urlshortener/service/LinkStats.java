package com.vietnguyen.urlshortener.service;

import java.time.LocalDate;
import java.util.Map;

/** Aggregated click analytics for a short link. */
public record LinkStats(
    String shortCode,
    long totalClicks,
    Map<LocalDate, Long> clicksByDay,
    Map<String, Long> referrers,
    Map<String, Long> userAgents) {

  /** Copies analytics maps so the response cannot be mutated externally. */
  public LinkStats {
    clicksByDay = Map.copyOf(clicksByDay);
    referrers = Map.copyOf(referrers);
    userAgents = Map.copyOf(userAgents);
  }
}
