package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.service.ClickRecorder;
import com.vietnguyen.urlshortener.service.LinkService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public redirect endpoint for short links. */
@RestController
public class RedirectController {

  private final LinkService linkService;
  private final ClickRecorder clickRecorder;

  /** Creates a redirect controller backed by the link service. */
  public RedirectController(LinkService linkService, ClickRecorder clickRecorder) {
    this.linkService = linkService;
    this.clickRecorder = clickRecorder;
  }

  @GetMapping("/{code}")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redirects to the link destination"),
    @ApiResponse(
        responseCode = "404",
        description = "Short code is unknown",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "500",
        description = "An unexpected failure occurred while resolving the link",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
    Link link = linkService.resolve(code);
    clickRecorder.record(link, request);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.destination()))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
