package com.vietnguyen.urlshortener.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vietnguyen.urlshortener.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LinkRepositoryTest {

  @Autowired private LinkRepository linkRepository;

  @Autowired private ClickEventRepository clickEventRepository;

  @Test
  void savesAndFindsLinkByShortCode() {
    Link link =
        new Link(
            null,
            "abc1234",
            "https://example.com",
            LinkStatus.ACTIVE,
            Instant.now().truncatedTo(ChronoUnit.MICROS));

    Link saved = linkRepository.save(link);

    assertThat(saved.id()).isNotNull();
    assertThat(linkRepository.findByShortCode("abc1234")).contains(saved);
  }

  @Test
  void rejectsDuplicateShortCodeWithDatabaseConstraint() {
    linkRepository.save(
        new Link(null, "duplicate", "https://one.example", LinkStatus.ACTIVE, Instant.now()));

    assertThatThrownBy(
            () ->
                linkRepository.save(
                    new Link(
                        null,
                        "duplicate",
                        "https://two.example",
                        LinkStatus.ACTIVE,
                        Instant.now())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void savesClickEventForLink() {
    Link link =
        linkRepository.save(
            new Link(null, "click123", "https://example.com", LinkStatus.ACTIVE, Instant.now()));

    ClickEvent saved =
        clickEventRepository.save(
            new ClickEvent(
                null,
                link.id(),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                "https://referrer.example",
                "test-agent",
                new ClientIp("127.0.0.1")));

    assertThat(saved.id()).isNotNull();
    assertThat(clickEventRepository.findByLinkId(link.id())).containsExactly(saved);
  }
}
