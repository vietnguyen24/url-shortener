package com.vietnguyen.urlshortener.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Validates destination URLs before they are persisted. */
@Component
public final class UrlValidator {

  private static final int MAX_URL_LENGTH = 2048;
  private static final long DNS_TIMEOUT_MILLIS = 500;
  private static final HostResolver DEFAULT_RESOLVER = UrlValidator::resolveHost;

  private final HostResolver hostResolver;

  /** Creates a validator using the bounded system DNS resolver. */
  public UrlValidator() {
    this(DEFAULT_RESOLVER);
  }

  UrlValidator(HostResolver hostResolver) {
    this.hostResolver = hostResolver;
  }

  /** Returns whether a destination uses an allowed scheme and public host. */
  public boolean isValid(String destination) {
    if (destination == null || destination.isBlank() || destination.length() > MAX_URL_LENGTH) {
      return false;
    }

    URI uri;
    try {
      uri = new URI(destination);
    } catch (URISyntaxException exception) {
      return false;
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null
        || host == null
        || (!scheme.toLowerCase(Locale.ROOT).equals("http")
            && !scheme.toLowerCase(Locale.ROOT).equals("https"))
        || uri.getUserInfo() != null) {
      return false;
    }

    try {
      for (InetAddress address : hostResolver.resolve(stripIpv6Brackets(host))) {
        if (isPrivate(address)) {
          return false;
        }
      }
      return true;
    } catch (UnknownHostException exception) {
      return false;
    }
  }

  private boolean isPrivate(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isUniqueLocalAddress(address);
  }

  private boolean isUniqueLocalAddress(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
  }

  private static InetAddress[] resolveHost(String host) throws UnknownHostException {
    try {
      return CompletableFuture.supplyAsync(
              () -> {
                try {
                  return InetAddress.getAllByName(host);
                } catch (UnknownHostException exception) {
                  throw new CompletionException(exception);
                }
              })
          .orTimeout(DNS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
          .join();
    } catch (CompletionException exception) {
      UnknownHostException unknownHostException = new UnknownHostException(host);
      unknownHostException.initCause(exception);
      throw unknownHostException;
    }
  }

  private String stripIpv6Brackets(String host) {
    if (host.startsWith("[") && host.endsWith("]")) {
      return host.substring(1, host.length() - 1);
    }
    return host;
  }
}
