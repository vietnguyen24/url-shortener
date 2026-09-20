package com.vietnguyen.urlshortener.service;

/** Generates opaque codes used to identify shortened links. */
public interface ShortCodeGenerator {

  /**
   * Generates a new candidate code.
   *
   * @return an opaque short code
   */
  String generate();
}
