package com.vietnguyen.urlshortener.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request, exposing it on the response and in the logging context
 * so every log line emitted while handling the request can be tied back to it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER_NAME = "X-Correlation-Id";
  static final String MDC_KEY = "correlationId";

  private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[a-zA-Z0-9-]{1,128}");

  private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = sanitize(request.getHeader(HEADER_NAME));
    MDC.put(MDC_KEY, correlationId);
    response.setHeader(HEADER_NAME, correlationId);
    try {
      LOGGER.info("Handling request {} {}", request.getMethod(), request.getRequestURI());
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /**
   * Accepts an incoming correlation id only when it is safe to echo back in a header and log line;
   * otherwise generates a fresh one. This rejects control characters (such as CR/LF) that could
   * otherwise be used for HTTP response-splitting or log injection.
   */
  private static String sanitize(String incoming) {
    if (incoming != null && SAFE_CORRELATION_ID.matcher(incoming).matches()) {
      return incoming;
    }
    return UUID.randomUUID().toString();
  }
}
