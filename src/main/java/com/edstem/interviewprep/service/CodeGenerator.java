package com.edstem.interviewprep.service;

import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Random base62 codes: only {@code [0-9A-Za-z]}, so they are URL-safe without encoding. 62^7 ≈ 3.5
 * trillion combinations keeps collisions rare; the service retries on the ones that happen.
 */
@Component
public class CodeGenerator {

  static final int LENGTH = 7;
  private static final String ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private final RandomGenerator random;

  public CodeGenerator(RandomGenerator random) {
    this.random = random;
  }

  public String next() {
    StringBuilder code = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
