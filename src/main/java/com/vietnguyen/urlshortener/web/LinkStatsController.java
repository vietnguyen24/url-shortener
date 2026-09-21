package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.service.LinkStatsService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management endpoint for click analytics. */
@RestController
@RequestMapping("/api/links")
public class LinkStatsController {

  private final LinkStatsService linkStatsService;

  /** Creates a controller backed by the analytics service. */
  public LinkStatsController(LinkStatsService linkStatsService) {
    this.linkStatsService = linkStatsService;
  }

  @GetMapping("/{code}/stats")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Aggregated click analytics"),
    @ApiResponse(
        responseCode = "404",
        description = "Short code is unknown",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "500",
        description = "An unexpected failure occurred while computing analytics",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<LinkStatsResponse> stats(@PathVariable String code) {
    return ResponseEntity.ok(LinkStatsResponse.from(linkStatsService.stats(code)));
  }
}
