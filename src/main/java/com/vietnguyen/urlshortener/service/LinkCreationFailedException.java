package com.vietnguyen.urlshortener.service;

/** Indicates that a link could not be assigned a unique short code. */
public class LinkCreationFailedException extends RuntimeException {

  /** Creates an exception preserving the persistence failure as its cause. */
  public LinkCreationFailedException(Throwable cause) {
    super("Unable to create a unique short code", cause);
  }
}
