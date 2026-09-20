package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.service.InvalidDestinationException;
import com.vietnguyen.urlshortener.service.LinkCreationFailedException;
import com.vietnguyen.urlshortener.service.LinkService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

  @ExceptionHandler(InvalidDestinationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ProblemResponse invalidDestination(InvalidDestinationException exception) {
    return new ProblemResponse("Invalid destination", exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ProblemResponse invalidRequest(MethodArgumentNotValidException exception) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .findFirst()
            .orElse("Request is invalid");
    return new ProblemResponse("Invalid request", detail);
  }

  @ExceptionHandler(LinkCreationFailedException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  ProblemResponse linkCreationFailed(LinkCreationFailedException exception) {
    return new ProblemResponse("Link creation failed", exception.getMessage());
  }

  record CreateLinkRequest(@NotBlank String destination) {}

  record CreateLinkResponse(String shortCode, String shortUrl, String destination) {}

  record ProblemResponse(String title, String detail) {}
}
