package com.vietnguyen.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import com.vietnguyen.urlshortener.service.InvalidDestinationException;
import com.vietnguyen.urlshortener.service.LinkService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LinkController.class)
@AutoConfigureMetrics
class LinkControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private MeterRegistry meterRegistry;

  @MockitoBean private LinkService linkService;

  @Test
  void createsLinkAndReturns201() throws Exception {
    when(linkService.create("https://example.com"))
        .thenReturn(
            new Link(1L, "abc1234", "https://example.com", LinkStatus.ACTIVE, Instant.now()));

    mockMvc
        .perform(
            post("/api/links")
                .header("X-API-Key", "dev-key-not-a-secret")
                .contentType("application/json")
                .content("{\"destination\":\"https://example.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shortCode").isNotEmpty())
        .andExpect(jsonPath("$.destination").value("https://example.com"));
  }

  @Test
  void incrementsLinksCreatedCounterOnSuccessfulCreation() throws Exception {
    when(linkService.create("https://example.com"))
        .thenReturn(
            new Link(1L, "abc1234", "https://example.com", LinkStatus.ACTIVE, Instant.now()));
    double before = meterRegistry.get("links.created").counter().count();

    mockMvc.perform(
        post("/api/links")
            .contentType("application/json")
            .content("{\"destination\":\"https://example.com\"}"));

    assertThat(meterRegistry.get("links.created").counter().count()).isEqualTo(before + 1.0);
  }

  @Test
  void rejectsInvalidDestinationWith400ProblemBody() throws Exception {
    when(linkService.create("javascript:alert(1)")).thenThrow(new InvalidDestinationException());

    mockMvc
        .perform(
            post("/api/links")
                .header("X-API-Key", "dev-key-not-a-secret")
                .contentType("application/json")
                .content("{\"destination\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid destination"));
  }

  @Test
  void rejectsBlankDestinationWith400ProblemBody() throws Exception {
    mockMvc
        .perform(
            post("/api/links")
                .header("X-API-Key", "dev-key-not-a-secret")
                .contentType("application/json")
                .content("{\"destination\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"));
  }
}
