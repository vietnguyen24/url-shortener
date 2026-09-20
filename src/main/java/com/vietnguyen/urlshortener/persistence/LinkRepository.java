package com.vietnguyen.urlshortener.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/** Persists and looks up shortened links. */
public interface LinkRepository extends CrudRepository<Link, Long> {

  /** Finds a link by its public short code. */
  Optional<Link> findByShortCode(String shortCode);
}
