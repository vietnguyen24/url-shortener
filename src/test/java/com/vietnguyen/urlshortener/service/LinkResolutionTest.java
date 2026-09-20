package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkResolutionTest {

  @Mock private LinkRepository linkRepository;

  @Test
  void resolvesActiveLinkByShortCode() {
    Link link = new Link(1L, "abc1234", "https://example.com", LinkStatus.ACTIVE, Instant.now());
    when(linkRepository.findByShortCode("abc1234")).thenReturn(Optional.of(link));
    LinkService service = new LinkService(linkRepository, null, null);

    assertThat(service.resolve("abc1234")).isEqualTo(link);
  }

  @Test
  void throwsNotFoundWhenCodeDoesNotExist() {
    when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());
    LinkService service = new LinkService(linkRepository, null, null);

    assertThatThrownBy(() -> service.resolve("missing")).isInstanceOf(LinkNotFoundException.class);
  }
}
