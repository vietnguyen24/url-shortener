package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vietnguyen.urlshortener.TestcontainersConfiguration;
import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LinkServiceIntegrationTest {

  @Autowired private LinkService linkService;

  @Autowired private LinkRepository linkRepository;

  @MockitoBean private ShortCodeGenerator shortCodeGenerator;

  @Test
  void retriesAfterRealPostgresUniqueConstraintCollision() {
    linkRepository.save(
        new Link(null, "collision", "https://existing.example", LinkStatus.ACTIVE, Instant.now()));
    when(shortCodeGenerator.generate()).thenReturn("collision", "retry01");

    Link created = linkService.create("https://example.com/new");

    assertThat(created.shortCode()).isEqualTo("retry01");
  }
}
