package com.vietnguyen.urlshortener.service;

import java.security.SecureRandom;

final class SecureRandomShortCodeGenerator implements ShortCodeGenerator {

  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final int CODE_LENGTH = 7;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String generate() {
    char[] code = new char[CODE_LENGTH];
    for (int index = 0; index < code.length; index++) {
      code[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
    }
    return new String(code);
  }
}
