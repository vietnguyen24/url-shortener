package com.vietnguyen.urlshortener.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vietnguyen.urlshortener.service.LinkNotFoundException;
import com.vietnguyen.urlshortener.service.LinkStats;
import com.vietnguyen.urlshortener.service.LinkStatsService;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LinkStatsController.class)
@Import(GlobalExceptionHandler.class)
class LinkStatsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LinkStatsService linkStatsService;

  @Test
  void returnsAggregatedStatsForCode() throws Exception {
    when(linkStatsService.stats("abc1234"))
        .thenReturn(
            new LinkStats(
                "abc1234",
                3L,
                Map.of(LocalDate.of(2026, 9, 18), 2L, LocalDate.of(2026, 9, 19), 1L),
                Map.of("https://search.example", 2L, "https://social.example", 1L),
                Map.of("Mozilla", 2L, "Safari", 1L)));

    mockMvc
        .perform(get("/api/links/abc1234/stats"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"shortCode\":\"abc1234\","
                        + "\"totalClicks\":3,"
                        + "\"clicksByDay\":{\"2026-09-18\":2,\"2026-09-19\":1},"
                        + "\"referrers\":{\"https://search.example\":2,\"https://social.example\":1},"
                        + "\"userAgents\":{\"Mozilla\":2,\"Safari\":1}}"));
  }

  @Test
  void returns404ForUnknownCode() throws Exception {
    when(linkStatsService.stats("missing")).thenThrow(new LinkNotFoundException("missing"));

    mockMvc
        .perform(get("/api/links/missing/stats"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Link not found"))
        .andExpect(jsonPath("$.status").value(404));
  }
}
