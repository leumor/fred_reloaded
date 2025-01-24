package hyphanet.crypt.io;

import static org.junit.jupiter.api.Assertions.*;

import hyphanet.crypt.exception.AeadVerificationFailedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AeadStreamsTest {

  @Test
  void testSuccessfulRoundTrip() throws IOException {
    Random random = new Random(0x96231307L);
    for (int i = 0; i < 10; i++) {
      var input = new byte[65536];
      random.nextBytes(input);
      checkSuccessfulRoundTrip(16, random, input);
      checkSuccessfulRoundTrip(24, random, input);
      checkSuccessfulRoundTrip(32, random, input);
    }
  }

  @Test
  void testCorruptedRoundTrip() throws IOException {
    Random random = new Random(0x96231307L); // Same seed as first test, intentionally.
    for (int i = 0; i < 10; i++) {
      var input = new byte[65536];
      random.nextBytes(input);
      checkFailedCorruptedRoundTrip(16, random, input);
      checkFailedCorruptedRoundTrip(24, random, input);
      checkFailedCorruptedRoundTrip(32, random, input);
    }
  }

  @Test
  void testTruncatedReadsWritesRoundTrip() throws IOException {
    Random random = new Random(0x49ee92f5);
    var input = new byte[512 * 1024];
    checkSuccessfulRoundTripRandomSplits(16, random, input);
    checkSuccessfulRoundTripRandomSplits(24, random, input);
    checkSuccessfulRoundTripRandomSplits(32, random, input);
  }

  /** Check whether we can close the stream early. */
  @Test
  void testCloseEarly() throws IOException {
    Random random = new Random(0x47f6709f);

    var input = new byte[2048];
    random.nextBytes(input);

    byte[] key = new byte[16];
    random.nextBytes(key);

    byte[] output;
    try (var os = new ByteArrayOutputStream()) {
      try (var cos = AeadOutputStream.innerCreateAes(os, key, random)) {
        cos.write(input, 0, input.length);
      }

      // Must close the AeadOutputStream before reading the output.
      output = os.toByteArray();
    }

    try (var is = new ByteArrayInputStream(output);
        var cis = AeadInputStream.createAes(is, key)) {
      byte[] first1KReadEncrypted = new byte[1024];
      new DataInputStream(cis).readFully(first1KReadEncrypted);
      byte[] first1KReadOriginal = new byte[1024];
      new DataInputStream(new ByteArrayInputStream(input)).readFully(first1KReadOriginal);
      assertArrayEquals(first1KReadEncrypted, first1KReadOriginal);
    }
  }

  /**
   * If we close the stream early but there is garbage after that point, it should throw on close().
   */
  @Test
  void testGarbageAfterClose() throws IOException {
    Random random = new Random(0x47f6709f);

    var input = new byte[1024];
    random.nextBytes(input);

    byte[] key = new byte[16];
    random.nextBytes(key);

    byte[] output;
    try (var os = new ByteArrayOutputStream()) {
      try (var cos =
          AeadOutputStream.innerCreateAes(new NoCloseProxyOutputStream(os), key, random)) {
        cos.write(input, 0, input.length);
      }

      // Must close the AeadOutputStream before reading the output.
      var origOutput = os.toByteArray();

      var garbage = new byte[1024];
      random.nextBytes(garbage);

      output = new byte[origOutput.length + garbage.length];

      System.arraycopy(origOutput, 0, output, 0, origOutput.length);
      System.arraycopy(garbage, 0, output, origOutput.length, garbage.length);
    }

    try (var is = new ByteArrayInputStream(output)) {
      var cis = AeadInputStream.createAes(is, key);

      byte[] first1KReadEncrypted = new byte[1024];
      new DataInputStream(cis).readFully(first1KReadEncrypted);
      byte[] first1KReadOriginal = new byte[1024];
      new DataInputStream(new ByteArrayInputStream(input)).readFully(first1KReadOriginal);
      assertArrayEquals(first1KReadEncrypted, first1KReadOriginal);

      assertThrows(
          AeadVerificationFailedException.class,
          cis::close,
          "Hash should be bogus due to garbage data at end");
    }
  }

  private void checkSuccessfulRoundTrip(int keySize, Random random, byte[] input)
      throws IOException {
    byte[] key = new byte[keySize];
    random.nextBytes(key);

    byte[] output;
    try (var os = new ByteArrayOutputStream()) {
      try (var cos = AeadOutputStream.innerCreateAes(os, key, random)) {
        cos.write(input);
      }

      // Must close the AeadOutputStream before reading the output.
      output = os.toByteArray();
    }

    assertTrue(output.length > input.length);

    byte[] decoded;
    try (var is = new ByteArrayInputStream(output);
        var cis = AeadInputStream.createAes(is, key)) {
      decoded = cis.readAllBytes();
    }

    assertArrayEquals(decoded, input);
  }

  private void checkFailedCorruptedRoundTrip(int keySize, Random random, byte[] input)
      throws IOException {
    byte[] key = new byte[keySize];
    random.nextBytes(key);

    byte[] output;
    try (var os = new ByteArrayOutputStream()) {
      CorruptingOutputStream kos =
          new CorruptingOutputStream(os, 16L, input.length + 16, 10, random);

      try (var cos = AeadOutputStream.innerCreateAes(kos, key, random)) {
        cos.write(input);
      }

      // Must close the AeadOutputStream before reading the output.
      output = os.toByteArray();
    }
    assertTrue(output.length > input.length);

    try (var is = new ByteArrayInputStream(output)) {
      var cis = AeadInputStream.createAes(is, key);
      assertThrows(
          AeadVerificationFailedException.class,
          cis::readAllBytes,
          "Checksum error should have been seen when read to the end of the stream.");
      assertThrows(
          AeadVerificationFailedException.class,
          cis::close,
          "Checksum error should have been seen when closing the stream.");
    }
  }

  private void checkSuccessfulRoundTripRandomSplits(int keySize, Random random, byte[] input)
      throws IOException {
    byte[] key = new byte[keySize];
    random.nextBytes(key);

    byte[] output;
    try (var os = new ByteArrayOutputStream()) {
      try (var cos =
          new RandomShortWriteOutputStream(
              AeadOutputStream.innerCreateAes(os, key, random), random)) {
        cos.write(input);
      }

      // Must close the AeadOutputStream before reading the output.
      output = os.toByteArray();
    }

    assertTrue(output.length > input.length);

    byte[] decoded;
    try (var is = new ByteArrayInputStream(output);
        var cis = new RandomShortReadInputStream(AeadInputStream.createAes(is, key), random)) {
      decoded = cis.readAllBytes();
    }

    assertArrayEquals(decoded, input);
  }
}
