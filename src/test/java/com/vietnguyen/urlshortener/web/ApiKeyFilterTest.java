package com.vietnguyen.urlshortener.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import com.vietnguyen.urlshortener.service.ClickRecorder;
import com.vietnguyen.urlshortener.service.LinkService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiKeyFilterTest {

  private LinkService linkService;
  private ClickRecorder clickRecorder;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    linkService = org.mockito.Mockito.mock(LinkService.class);
    clickRecorder = org.mockito.Mockito.mock(ClickRecorder.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new LinkController(linkService), redirectController())
            .addFilters(new ApiKeyFilter("test-key"))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void rejectsMissingApiKeyOnManagementEndpoint() throws Exception {
    mockMvc
        .perform(post("/api/links"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"));

    verify(linkService, never()).create(any());
  }

  @Test
  void rejectsInvalidApiKeyOnManagementEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/links/abc1234/stats").header("X-API-Key", "wrong-key"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void allowsValidApiKeyOnManagementEndpoint() throws Exception {
    when(linkService.create("https://example.com"))
        .thenReturn(
            new Link(1L, "abc1234", "https://example.com", LinkStatus.ACTIVE, Instant.now()));

    mockMvc
        .perform(
            post("/api/links")
                .header("X-API-Key", "test-key")
                .contentType("application/json")
                .content("{\"destination\":\"https://example.com\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void leavesRedirectPublic() throws Exception {
    when(linkService.resolve("abc1234"))
        .thenReturn(
            new Link(1L, "abc1234", "https://example.com", LinkStatus.ACTIVE, Instant.now()));

    mockMvc
        .perform(get("/abc1234"))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com"));
  }

  @Test
  void leavesHealthPublic() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    new ApiKeyFilter("test-key").doFilter(request, response, chain);

    org.junit.jupiter.api.Assertions.assertSame(request, chain.getRequest());
  }

  private RedirectController redirectController() {
    return new RedirectController(linkService, clickRecorder);
  }
}
