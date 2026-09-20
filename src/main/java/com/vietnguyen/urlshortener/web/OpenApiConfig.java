package com.vietnguyen.urlshortener.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes the OpenAPI contract metadata exposed at {@code /v3/api-docs}. */
@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI urlShortenerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("URL Shortener API")
                .version("v1")
                .description(
                    "Public API for creating short links, resolving redirects, and reading"
                        + " click analytics."));
  }
}
