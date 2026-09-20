package com.vietnguyen.urlshortener.service;

import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Builds click analytics for management requests. */
@Service
public class LinkStatsService {

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickEventRepository;

  /** Creates the analytics service with its repositories. */
  public LinkStatsService(
      LinkRepository linkRepository, ClickEventRepository clickEventRepository) {
    this.linkRepository = linkRepository;
    this.clickEventRepository = clickEventRepository;
  }

  /** Aggregates click events for a short code. */
  public LinkStats stats(String shortCode) {
    var link =
        linkRepository
            .findByShortCode(Objects.requireNonNull(shortCode))
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    List<ClickEvent> events = clickEventRepository.findByLinkId(link.id());
    return new LinkStats(
        link.shortCode(),
        events.size(),
        events.stream()
            .collect(
                Collectors.groupingBy(
                    event -> event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(),
                    Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(toLinkedMap()),
        countNonNull(events, ClickEvent::referrer),
        countNonNull(events, ClickEvent::userAgent));
  }

  private static Map<String, Long> countNonNull(
      List<ClickEvent> events, Function<ClickEvent, String> valueExtractor) {
    return events.stream()
        .map(valueExtractor)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .collect(toLinkedMap());
  }

  private static <K, V> java.util.stream.Collector<Map.Entry<K, V>, ?, Map<K, V>> toLinkedMap() {
    return Collectors.toMap(
        Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new);
  }
}
