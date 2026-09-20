package com.vietnguyen.urlshortener.web;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.service.LinkNotFoundException;
import com.vietnguyen.urlshortener.service.LinkService;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public redirect endpoint for short links. */
@RestController
public class RedirectController {

  private final LinkService linkService;

  /** Creates a redirect controller backed by the link service. */
  public RedirectController(LinkService linkService) {
    this.linkService = linkService;
  }

  @GetMapping("/{code}")
  ResponseEntity<Void> redirect(@PathVariable String code) {
    Link link = linkService.resolve(code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.destination()))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  @ExceptionHandler(LinkNotFoundException.class)
  ResponseEntity<Void> notFound() {
    return ResponseEntity.notFound().build();
  }
}
