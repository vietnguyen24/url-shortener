package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.service.LinkService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** HTTP API for creating short links. */
@RestController
@RequestMapping("/api/links")
public class LinkController {

  private final LinkService linkService;
  private final Counter linksCreatedCounter;

  /** Creates a controller backed by the link creation service. */
  public LinkController(LinkService linkService, MeterRegistry meterRegistry) {
    this.linkService = linkService;
    this.linksCreatedCounter = meterRegistry.counter("links.created");
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Link created"),
    @ApiResponse(
        responseCode = "400",
        description = "Destination is missing or invalid",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "500",
        description = "A unique short code could not be created",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  CreateLinkResponse create(@Valid @RequestBody CreateLinkRequest request) {
    Link link = linkService.create(request.destination());
    linksCreatedCounter.increment();
    String shortUrl =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/{code}")
            .buildAndExpand(link.shortCode())
            .toUriString();
    return new CreateLinkResponse(link.shortCode(), shortUrl, link.destination());
  }

  record CreateLinkRequest(@NotBlank String destination) {}

  record CreateLinkResponse(String shortCode, String shortUrl, String destination) {}
}
