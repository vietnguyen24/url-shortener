package com.vietnguyen.urlshortener.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vietnguyen.urlshortener.service.InvalidDestinationException;
import com.vietnguyen.urlshortener.service.LinkCreationFailedException;
import com.vietnguyen.urlshortener.service.LinkNotFoundException;
import com.vietnguyen.urlshortener.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({LinkController.class, RedirectController.class})
@AutoConfigureMetrics
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LinkService linkService;

  @MockitoBean private com.vietnguyen.urlshortener.service.ClickRecorder clickRecorder;

  @Test
  void invalidDestinationReturnsRfc7807Problem() throws Exception {
    when(linkService.create("javascript:alert(1)")).thenThrow(new InvalidDestinationException());

    mockMvc
        .perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Invalid destination"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Destination must be a valid public HTTP(S) URL"))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void unknownCodeReturnsRfc7807Problem() throws Exception {
    when(linkService.resolve("missing")).thenThrow(new LinkNotFoundException("missing"));

    mockMvc
        .perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Link not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void malformedJsonReturnsRfc7807Problem() throws Exception {
    mockMvc
        .perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void creationFailureReturnsRfc7807ProblemWithoutCauseDetails() throws Exception {
    when(linkService.create("https://example.com"))
        .thenThrow(new LinkCreationFailedException(new IllegalStateException("database secret")));

    mockMvc
        .perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"https://example.com\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Link creation failed"))
        .andExpect(jsonPath("$.detail").value("Unable to create a unique short code"))
        .andExpect(jsonPath("$.trace").doesNotExist())
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @Test
  void unexpectedFailureReturnsGenericProblemWithoutExceptionMessage() throws Exception {
    when(linkService.create("https://example.com"))
        .thenThrow(new IllegalStateException("internal secret"));

    mockMvc
        .perform(
            post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"https://example.com\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.detail").value("The request could not be completed"))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void unsupportedMethodPreserves405ProblemResponse() throws Exception {
    mockMvc
        .perform(get("/api/links"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void unsupportedMediaTypePreserves415ProblemResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/links").contentType(MediaType.TEXT_PLAIN).content("https://example.com"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }
}
