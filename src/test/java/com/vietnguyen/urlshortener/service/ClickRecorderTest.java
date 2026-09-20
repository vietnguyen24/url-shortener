package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.ClientIp;
import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class ClickRecorderTest {

  private final ClickEventRepository clickEventRepository =
      org.mockito.Mockito.mock(ClickEventRepository.class);
  private final ClickRecorder clickRecorder = new ClickRecorder(clickEventRepository);

  @Test
  void recordsRequestMetadataForResolvedLink() {
    MockHttpServletRequest request = requestWithMetadata();
    Link link = link();

    clickRecorder.record(link, request);

    ArgumentCaptor<ClickEvent> eventCaptor = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEventRepository).save(eventCaptor.capture());
    ClickEvent event = eventCaptor.getValue();
    org.assertj.core.api.Assertions.assertThat(event.linkId()).isEqualTo(42L);
    org.assertj.core.api.Assertions.assertThat(event.occurredAt()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(event.referrer())
        .isEqualTo("https://referrer.example/page");
    org.assertj.core.api.Assertions.assertThat(event.userAgent()).isEqualTo("test-agent");
    org.assertj.core.api.Assertions.assertThat(event.clientIp())
        .isEqualTo(new ClientIp("192.0.2.10"));
  }

  @Test
  void swallowsClickWriteFailureSoRedirectCanContinue() {
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(clickEventRepository)
        .save(any(ClickEvent.class));

    assertThatCode(() -> clickRecorder.record(link(), requestWithMetadata()))
        .doesNotThrowAnyException();
  }

  private static MockHttpServletRequest requestWithMetadata() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("Referer", "https://referrer.example/page");
    request.addHeader("User-Agent", "test-agent");
    return request;
  }

  private static Link link() {
    return new Link(42L, "abc1234", "https://example.com/path", LinkStatus.ACTIVE, Instant.now());
  }
}
