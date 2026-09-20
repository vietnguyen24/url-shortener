package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class UrlValidatorTest {

  @Test
  void acceptsPublicHttpAndHttpsUrls() {
    UrlValidator validator = new UrlValidator(host -> new InetAddress[] {publicAddress()});

    assertThat(validator.isValid("https://example.com/path")).isTrue();
    assertThat(validator.isValid("http://example.com")).isTrue();
  }

  @Test
  void rejectsUnsupportedSchemesMalformedUrlsPrivateHostsAndLongUrls() {
    UrlValidator validator =
        new UrlValidator(
            host -> {
              if (host.equals("127.0.0.1") || host.equals("localhost")) {
                return new InetAddress[] {InetAddress.getLoopbackAddress()};
              }
              return new InetAddress[] {publicAddress()};
            });

    assertThat(validator.isValid("javascript:alert(1)")).isFalse();
    assertThat(validator.isValid("not a url")).isFalse();
    assertThat(validator.isValid("http://127.0.0.1/admin")).isFalse();
    assertThat(validator.isValid("http://localhost/admin")).isFalse();
    assertThat(validator.isValid("https://example.com/" + "a".repeat(2048))).isFalse();
  }

  @Test
  void rejectsIpv6UniqueLocalAddresses() {
    UrlValidator validator =
        new UrlValidator(host -> new InetAddress[] {InetAddress.getByName(host)});

    assertThat(validator.isValid("http://[fd00::1]/")).isFalse();
  }

  private static InetAddress publicAddress() {
    try {
      return InetAddress.getByName("93.184.216.34");
    } catch (UnknownHostException exception) {
      throw new AssertionError(exception);
    }
  }
}
