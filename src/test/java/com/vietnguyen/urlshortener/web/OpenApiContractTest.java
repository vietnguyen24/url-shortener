package com.vietnguyen.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vietnguyen.urlshortener.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the springdoc-generated OpenAPI contract exposes the current public API surface and that
 * the committed {@code docs/openapi.json} snapshot is kept in sync with it.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class OpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void apiDocsExposeCreateRedirectStatsAndErrorSurface() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    JsonNode root = MAPPER.readTree(result.getResponse().getContentAsByteArray());
    JsonNode paths = root.path("paths");

    JsonNode createOperation = paths.path("/api/links").path("post");
    assertThat(createOperation.isMissingNode()).isFalse();
    assertThat(createOperation.path("responses").has("201")).isTrue();
    assertThat(createOperation.path("responses").has("400")).isTrue();
    assertThat(createOperation.path("responses").has("500")).isTrue();

    JsonNode redirectOperation = paths.path("/{code}").path("get");
    assertThat(redirectOperation.isMissingNode()).isFalse();
    assertThat(redirectOperation.path("responses").has("302")).isTrue();
    assertThat(redirectOperation.path("responses").has("404")).isTrue();
    assertThat(redirectOperation.path("responses").has("500")).isTrue();

    JsonNode statsOperation = paths.path("/api/links/{code}/stats").path("get");
    assertThat(statsOperation.isMissingNode()).isFalse();
    assertThat(statsOperation.path("responses").has("200")).isTrue();
    assertThat(statsOperation.path("responses").has("404")).isTrue();
    assertThat(statsOperation.path("responses").has("500")).isTrue();

    assertProblemJsonContent(createOperation, "400");
    assertProblemJsonContent(createOperation, "500");
    assertProblemJsonContent(redirectOperation, "404");
    assertProblemJsonContent(redirectOperation, "500");
    assertProblemJsonContent(statsOperation, "404");
    assertProblemJsonContent(statsOperation, "500");
  }

  private static void assertProblemJsonContent(JsonNode operation, String responseCode) {
    JsonNode content = operation.path("responses").path(responseCode).path("content");
    assertThat(content.has("application/problem+json"))
        .as("%s response %s must declare application/problem+json content", operation, responseCode)
        .isTrue();
  }

  @Test
  void committedOpenApiJsonMatchesGeneratedContractPathsAndComponents() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    JsonNode generated = MAPPER.readTree(result.getResponse().getContentAsByteArray());

    Path committedPath = Path.of("docs", "openapi.json");
    assertThat(Files.exists(committedPath)).as("docs/openapi.json must be committed").isTrue();
    JsonNode committed = MAPPER.readTree(Files.readString(committedPath));

    assertThat(committed.path("paths")).isEqualTo(generated.path("paths"));
    assertThat(committed.path("components")).isEqualTo(generated.path("components"));
  }
}
