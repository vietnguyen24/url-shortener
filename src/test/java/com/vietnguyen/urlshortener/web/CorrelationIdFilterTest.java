package com.vietnguyen.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void generatesCorrelationIdAndExposesItDuringRequestHandling() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get("correlationId"));

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isNotBlank();
    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(mdcDuringChain.get());
    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void reusesIncomingCorrelationIdHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "given-id-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("given-id-123");
  }

  @Test
  void generatesFreshCorrelationIdWhenIncomingHeaderContainsUnsafeCharacters() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "bad\r\nSet-Cookie: evil=1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Correlation-Id"))
        .doesNotContain("\r")
        .doesNotContain("\n")
        .matches("[a-zA-Z0-9-]+");
  }

  @Test
  void clearsMdcEvenWhenChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(request, response, chain))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get("correlationId")).isNull();
  }
}
