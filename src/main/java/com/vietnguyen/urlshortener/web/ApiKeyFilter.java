package com.vietnguyen.urlshortener.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects management endpoints with a shared API key. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";

  private final byte[] configuredKey;

  /** Creates a filter using the configured management API key. */
  public ApiKeyFilter(@Value("${security.api-key}") String apiKey) {
    configuredKey = apiKey.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    byte[] suppliedKey =
        request.getHeader(API_KEY_HEADER) == null
            ? new byte[0]
            : request.getHeader(API_KEY_HEADER).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(configuredKey, suppliedKey)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response
          .getWriter()
          .write(
              "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                  + "\"detail\":\"A valid API key is required\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
