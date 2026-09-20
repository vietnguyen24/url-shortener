package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LinkStatsServiceTest {

  private final LinkRepository linkRepository = Mockito.mock(LinkRepository.class);
  private final ClickEventRepository clickEventRepository =
      Mockito.mock(ClickEventRepository.class);
  private final LinkStatsService linkStatsService =
      new LinkStatsService(linkRepository, clickEventRepository);

  @Test
  void aggregatesSeededClickEventsByDayReferrerAndUserAgent() {
    Link link = new Link(42L, "abc1234", "https://example.com", LinkStatus.ACTIVE, Instant.now());
    when(linkRepository.findByShortCode("abc1234")).thenReturn(java.util.Optional.of(link));
    when(clickEventRepository.findByLinkId(42L))
        .thenReturn(
            List.of(
                event("2026-09-18T10:15:00Z", "https://search.example", "Mozilla"),
                event("2026-09-18T12:15:00Z", "https://search.example", "Mozilla"),
                event("2026-09-19T09:15:00Z", "https://social.example", "Safari")));

    LinkStats stats = linkStatsService.stats("abc1234");

    assertThat(stats.totalClicks()).isEqualTo(3);
    assertThat(stats.clicksByDay())
        .containsEntry(
            Instant.parse("2026-09-18T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDate(), 2L)
        .containsEntry(
            Instant.parse("2026-09-19T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDate(), 1L);
    assertThat(stats.referrers())
        .containsEntry("https://search.example", 2L)
        .containsEntry("https://social.example", 1L);
    assertThat(stats.userAgents()).containsEntry("Mozilla", 2L).containsEntry("Safari", 1L);
  }

  private static ClickEvent event(String occurredAt, String referrer, String userAgent) {
    return new ClickEvent(null, 42L, Instant.parse(occurredAt), referrer, userAgent, null);
  }
}
