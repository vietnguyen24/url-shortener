package com.vietnguyen.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecureRandomShortCodeGeneratorTest {

  private final ShortCodeGenerator generator = new SecureRandomShortCodeGenerator();

  @Test
  void generateReturnsSevenBase62Characters() {
    for (int attempt = 0; attempt < 1_000; attempt++) {
      assertThat(generator.generate()).matches("[0-9A-Za-z]{7}");
    }
  }
}
