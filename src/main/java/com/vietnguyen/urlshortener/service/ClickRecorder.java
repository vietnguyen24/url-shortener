package com.vietnguyen.urlshortener.service;

import com.vietnguyen.urlshortener.persistence.ClickEvent;
import com.vietnguyen.urlshortener.persistence.ClickEventRepository;
import com.vietnguyen.urlshortener.persistence.ClientIp;
import com.vietnguyen.urlshortener.persistence.Link;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Records redirect metadata without allowing analytics failures to affect redirects. */
@Service
public class ClickRecorder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClickRecorder.class);

  private final ClickEventRepository clickEventRepository;

  /** Creates a click recorder backed by the event repository. */
  public ClickRecorder(ClickEventRepository clickEventRepository) {
    this.clickEventRepository = clickEventRepository;
  }

  /** Attempts to record a redirect request and logs persistence failures. */
  public void record(Link link, HttpServletRequest request) {
    try {
      clickEventRepository.save(
          new ClickEvent(
              null,
              link.id(),
              Instant.now(),
              request.getHeader("Referer"),
              request.getHeader("User-Agent"),
              new ClientIp(request.getRemoteAddr())));
    } catch (RuntimeException exception) {
      LOGGER.warn("Unable to record redirect click for link {}", link.id(), exception);
    }
  }
}
