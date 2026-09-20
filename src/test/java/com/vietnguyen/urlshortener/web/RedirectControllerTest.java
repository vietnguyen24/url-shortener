package com.vietnguyen.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import com.vietnguyen.urlshortener.service.ClickRecorder;
import com.vietnguyen.urlshortener.service.LinkNotFoundException;
import com.vietnguyen.urlshortener.service.LinkService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
@AutoConfigureMetrics
@Import(ClickRecorder.class)
class RedirectControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private MeterRegistry meterRegistry;

  @MockitoBean private LinkService linkService;

  @MockitoBean private ClickEventRepository clickEventRepository;

  @Test
  void redirectsKnownCodeWithoutAllowingCaching() throws Exception {
    when(linkService.resolve("abc1234"))
        .thenReturn(
            new Link(1L, "abc1234", "https://example.com/path", LinkStatus.ACTIVE, Instant.now()));

    mockMvc
        .perform(get("/abc1234"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/path"))
        .andExpect(header().string("Cache-Control", "no-store"));

    verify(clickEventRepository).save(any(ClickEvent.class));
  }

  @Test
  void incrementsRedirectsServedCounterOnSuccessfulRedirect() throws Exception {
    when(linkService.resolve("abc1234")).thenReturn(link());
    double before = meterRegistry.get("redirects.served").counter().count();

    mockMvc.perform(get("/abc1234"));

    assertThat(meterRegistry.get("redirects.served").counter().count()).isEqualTo(before + 1.0);
  }

  @Test
  void incrementsRedirectsMissedCounterForUnknownCode() throws Exception {
    when(linkService.resolve("missing")).thenThrow(new LinkNotFoundException("missing"));
    double before = meterRegistry.get("redirects.missed").counter().count();

    mockMvc.perform(get("/missing"));

    assertThat(meterRegistry.get("redirects.missed").counter().count()).isEqualTo(before + 1.0);
  }

  @Test
  void returns302WhenClickWriteFails() throws Exception {
    when(linkService.resolve("abc1234")).thenReturn(link());
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(clickEventRepository)
        .save(any(ClickEvent.class));

    mockMvc
        .perform(get("/abc1234"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/path"));
  }

  @Test
  void returns404ForUnknownCode() throws Exception {
    when(linkService.resolve("missing")).thenThrow(new LinkNotFoundException("missing"));

    mockMvc.perform(get("/missing")).andExpect(status().isNotFound());
  }

  private static Link link() {
    return new Link(1L, "abc1234", "https://example.com/path", LinkStatus.ACTIVE, Instant.now());
  }
}
