package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

  @Mock private LinkRepository linkRepository;

  @Mock private ShortCodeGenerator shortCodeGenerator;

  @Test
  void retriesWithNewCodeWhenShortCodeCollides() {
    when(shortCodeGenerator.generate()).thenReturn("taken01", "fresh01");
    when(linkRepository.save(any(Link.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"))
        .thenReturn(
            new Link(1L, "fresh01", "https://example.com", LinkStatus.ACTIVE, Instant.now()));

    LinkService service = new LinkService(linkRepository, shortCodeGenerator, new UrlValidator());

    Link created = service.create("https://example.com");

    assertThat(created.shortCode()).isEqualTo("fresh01");
    verify(linkRepository, org.mockito.Mockito.times(2)).save(any(Link.class));
  }

  @Test
  void rejectsInvalidDestinationWithDomainException() {
    LinkService service =
        new LinkService(
            linkRepository,
            shortCodeGenerator,
            new UrlValidator(host -> new java.net.InetAddress[0]));

    assertThatThrownBy(() -> service.create("javascript:alert(1)"))
        .isInstanceOf(InvalidDestinationException.class);
  }
}
