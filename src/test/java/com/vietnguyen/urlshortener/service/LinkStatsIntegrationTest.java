package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vietnguyen.urlshortener.TestcontainersConfiguration;
import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.ClientIp;
import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LinkStatsIntegrationTest {

  @Autowired private LinkRepository linkRepository;

  @Autowired private ClickEventRepository clickEventRepository;

  @Autowired private LinkStatsService linkStatsService;

  @Test
  void returnsAnalyticsMatchingSeededClickEvents() {
    Link link =
        linkRepository.save(
            new Link(null, "stats123", "https://example.com", LinkStatus.ACTIVE, Instant.now()));
    clickEventRepository.save(
        new ClickEvent(
            null,
            link.id(),
            Instant.parse("2026-09-18T10:15:00Z"),
            "https://search.example",
            "Mozilla",
            new ClientIp("192.0.2.1")));
    clickEventRepository.save(
        new ClickEvent(
            null,
            link.id(),
            Instant.parse("2026-09-18T12:15:00Z"),
            "https://search.example",
            "Mozilla",
            new ClientIp("192.0.2.2")));
    clickEventRepository.save(
        new ClickEvent(
            null,
            link.id(),
            Instant.parse("2026-09-19T09:15:00Z"),
            "https://social.example",
            "Safari",
            new ClientIp("192.0.2.3")));

    LinkStats stats = linkStatsService.stats("stats123");

    assertThat(stats.totalClicks()).isEqualTo(3);
    assertThat(stats.clicksByDay())
        .containsEntry(LocalDate.of(2026, 9, 18), 2L)
        .containsEntry(LocalDate.of(2026, 9, 19), 1L);
    assertThat(stats.referrers())
        .containsEntry("https://search.example", 2L)
        .containsEntry("https://social.example", 1L);
    assertThat(stats.userAgents()).containsEntry("Mozilla", 2L).containsEntry("Safari", 1L);
  }
}
