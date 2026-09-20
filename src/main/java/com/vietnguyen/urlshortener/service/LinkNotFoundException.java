package com.vietnguyen.urlshortener.service;

/** Indicates that a requested short code does not identify a link. */
public class LinkNotFoundException extends RuntimeException {

  /** Creates an exception for a missing short code. */
  public LinkNotFoundException(String shortCode) {
    super("No link exists for short code: " + shortCode);
  }
}
