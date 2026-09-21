package com.vietnguyen.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import com.vietnguyen.urlshortener.service.LinkService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LinkService linkService;

  @MockitoBean private ClickEventRepository clickEventRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void logsRequestHandlingAsJsonCarryingTheRequestCorrelationId(CapturedOutput output)
      throws Exception {
    String correlationId = "test-correlation-8f2f";

    mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", correlationId));

    assertAllLinesForCorrelationIdAreJson(output, correlationId);
  }

  @Test
  void propagatesCorrelationIdToLogLinesEmittedByDownstreamServiceLayerCode(CapturedOutput output)
      throws Exception {
    String correlationId = "downstream-9c31";
    when(linkService.resolve("abc1234")).thenReturn(link());
    doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(clickEventRepository)
        .save(any(ClickEvent.class));

    mockMvc.perform(get("/abc1234").header("X-Correlation-Id", correlationId));

    List<String> matchingLines = assertAllLinesForCorrelationIdAreJson(output, correlationId);
    assertThat(matchingLines)
        .anySatisfy(
            line -> assertThat(line).contains("com.vietnguyen.urlshortener.service.ClickRecorder"));
  }

  private List<String> assertAllLinesForCorrelationIdAreJson(
      CapturedOutput output, String correlationId) {
    List<String> matchingLines =
        output.getOut().lines().filter(line -> line.contains(correlationId)).toList();

    assertThat(matchingLines).isNotEmpty();
    for (String line : matchingLines) {
      JsonNode json = objectMapper.readTree(line);
      assertThat(json.get("correlationId").asText()).isEqualTo(correlationId);
      assertThat(json.has("message")).isTrue();
    }
    return matchingLines;
  }

  private static Link link() {
    return new Link(1L, "abc1234", "https://example.com/path", LinkStatus.ACTIVE, Instant.now());
  }
}
