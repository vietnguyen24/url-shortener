package com.vietnguyen.urlshortener.service;

import com.vietnguyen.urlshortener.persistence.Link;
import com.vietnguyen.urlshortener.persistence.LinkRepository;
import com.vietnguyen.urlshortener.persistence.LinkStatus;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Creates persisted short links. */
@Service
public class LinkService {

  private static final int MAX_CREATE_ATTEMPTS = 3;

  private final LinkRepository linkRepository;
  private final ShortCodeGenerator shortCodeGenerator;
  private final UrlValidator urlValidator;

  /** Creates the link service with its persistence and validation dependencies. */
  public LinkService(
      LinkRepository linkRepository,
      ShortCodeGenerator shortCodeGenerator,
      UrlValidator urlValidator) {
    this.linkRepository = linkRepository;
    this.shortCodeGenerator = shortCodeGenerator;
    this.urlValidator = urlValidator;
  }

  /** Creates a short link, retrying database-enforced code collisions. */
  public Link create(String destination) {
    if (!urlValidator.isValid(destination)) {
      throw new InvalidDestinationException();
    }

    for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt++) {
      Link candidate =
          new Link(
              null, shortCodeGenerator.generate(), destination, LinkStatus.ACTIVE, Instant.now());
      try {
        return linkRepository.save(candidate);
      } catch (DataIntegrityViolationException exception) {
        if (attempt == MAX_CREATE_ATTEMPTS - 1) {
          throw new LinkCreationFailedException(exception);
        }
      }
    }
    throw new IllegalStateException("Link creation attempts exhausted");
  }

  /** Resolves a public short code or reports that it does not exist. */
  public Link resolve(String shortCode) {
    return linkRepository
        .findByShortCode(Objects.requireNonNull(shortCode))
        .orElseThrow(() -> new LinkNotFoundException(shortCode));
  }
}
