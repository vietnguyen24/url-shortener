package com.vietnguyen.urlshortener.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

/** Persists and queries redirect click events. */
public interface ClickEventRepository extends CrudRepository<ClickEvent, Long> {

  /** Finds all click events belonging to a link. */
  List<ClickEvent> findByLinkId(Long linkId);
}
