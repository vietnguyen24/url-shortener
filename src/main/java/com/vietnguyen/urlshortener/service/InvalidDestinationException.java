package com.vietnguyen.urlshortener.service;

/** Indicates that a submitted destination is not allowed. */
public class InvalidDestinationException extends RuntimeException {

  /** Creates an exception with the standard validation message. */
  public InvalidDestinationException() {
    super("Destination must be a valid public HTTP(S) URL");
  }
}
