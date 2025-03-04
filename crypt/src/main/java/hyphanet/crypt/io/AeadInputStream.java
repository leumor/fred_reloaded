package hyphanet.crypt.io;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.OCBBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * A decrypting and authenticating {@link InputStream} using Authenticated Encryption with
 * Associated Data (AEAD). This class reads encrypted data and verifies its integrity upon closing.
 * <b>Important:</b> Authentication occurs during stream closure - callers must ensure {@link
 * #close()} is executed even if exceptions occur.
 *
 * <p>Uses OCB (Offset Codebook Mode) with two AES ciphers: one for bulk data processing and another
 * for lightweight MAC computation. The nonce is read from the stream header.
 */
public class AeadInputStream extends FilterInputStream {

  /** Expected MAC size in bits, matches {@link AeadOutputStream#MAC_SIZE_BITS}. */
  private static final int MAC_SIZE_BITS = AeadOutputStream.MAC_SIZE_BITS;

  /**
   * Constructs an AEAD decrypting stream.
   *
   * @param is Source input stream containing encrypted data and nonce
   * @param key Cryptographic key (length must match cipher requirements)
   * @param hashCipher Lightweight cipher for MAC computation (e.g., {@code AESLightEngine})
   * @param mainCipher Primary cipher for data decryption (e.g., {@code AESEngine})
   * @throws IOException If nonce cannot be read from the stream
   * @throws IllegalArgumentException If cipher configurations are incompatible
   */
  public AeadInputStream(InputStream is, byte[] key, BlockCipher hashCipher, BlockCipher mainCipher)
      throws IOException {
    super(is);
    byte[] nonce = new byte[Math.min(mainCipher.getBlockSize(), 15)];

    // Do not use try-with-resources here, as we don't want to close the underlying
    // InputStream when readFully throws an Exception.
    DataInputStream dis = new DataInputStream(is);
    dis.readFully(nonce);

    cipher = new OCBBlockCipher(hashCipher, mainCipher);
    KeyParameter keyParam = new KeyParameter(key);
    AEADParameters params = new AEADParameters(keyParam, MAC_SIZE_BITS, nonce);
    cipher.init(false, params);
    excess = new byte[mainCipher.getBlockSize()];
    excessEnd = 0;
    excessPtr = 0;
  }

  /**
   * Factory method to create an AES-OCB AEAD stream.
   *
   * @param is Source input stream
   * @param key 256/192/128-bit AES key
   * @return Configured AES-OCB decrypting stream
   * @throws IOException If stream initialization fails
   */
  public static AeadInputStream createAes(InputStream is, byte[] key) throws IOException {
    var mainCipher = AESEngine.newInstance();
    AESLightEngine hashCipher = new AESLightEngine();
    return new AeadInputStream(is, key, hashCipher, mainCipher);
  }

  /**
   * Return IV/nonce size in bytes used by the underlying cipher
   *
   * @return IV/nonce size in bytes used by the underlying cipher
   */
  public final int getIVSize() {
    return cipher.getUnderlyingCipher().getBlockSize() / 8;
  }

  /**
   * Reads decrypted data, handling partial blocks and MAC verification at stream end.
   *
   * <p><b>Implementation Note:</b> Uses temporary buffers during decryption as AEAD ciphers may
   * produce more/fewer bytes than input. Excess decrypted bytes are cached for subsequent reads.
   *
   * @throws AeadVerificationFailedException If MAC validation fails during stream closure
   */
  @Override
  public int read() throws IOException {
    int length = read(oneByte);
    return length <= 0 ? -1 : oneByte[0] & 0xFF;
  }

  @Override
  public int read(byte[] buf) throws IOException {
    return read(buf, 0, buf.length);
  }

  /**
   * Decrypts data into the buffer, prioritizing cached excess bytes before reading new blocks.
   *
   * @param buf Target buffer
   * @param offset Start position in buffer
   * @param length Maximum bytes to read
   * @return Number of decrypted bytes read, or -1 if stream ended
   * @throws AeadVerificationFailedException If MAC check fails after exhausting stream
   */
  @Override
  public int read(byte[] buf, int offset, int length) throws IOException {
    if (length < 0 || offset < 0 || offset + length > buf.length) {
      throw new IndexOutOfBoundsException("Length cannot be negative");
    }
    if (length == 0) {
      return 0;
    }
    if (finished) {
      return -1;
    }

    var excessBytes = handleExcessBytes(buf, offset, length);
    if (excessBytes > 0) {
      return excessBytes;
    }

    // FIXME OPTIMISE Can we avoid allocating new buffers here? We can't safely use
    //  in=out when
    // calling cipher.processBytes().
    byte[] temp = new byte[length];
    int read = in.read(temp);
    if (read == 0) {
      return read; // Nasty ambiguous case.
    }
    if (read < 0) {
      // End of stream.
      // The last few bytes will still be in the cipher's buffer and have to be
      // retrieved by doFinal().
      try {
        excessEnd = cipher.doFinal(excess, 0);
      } catch (InvalidCipherTextException e) {
        throw new AeadVerificationFailedException();
      }
      finished = true;
      return excessEnd > 0 ? read(buf, offset, length) : -1;
    }

    int outLength = cipher.getUpdateOutputSize(read);
    if (outLength > length) {
      byte[] outputTemp = new byte[outLength];
      int decryptedBytes = cipher.processBytes(temp, 0, read, outputTemp, 0);
      assert decryptedBytes == outLength : "Decrypted bytes mismatch expected output size";
      System.arraycopy(outputTemp, 0, buf, offset, length);
      excessEnd = outLength - length;
      assert excessEnd < excess.length : "Excess buffer overflow";
      System.arraycopy(outputTemp, length, excess, 0, excessEnd);
      return length;
    } else {
      return cipher.processBytes(temp, 0, read, buf, offset);
    }
  }

  /**
   * @return Estimated available bytes including excess buffer and underlying stream. Note: Accuracy
   *     is limited as MAC bytes may not be fully read yet.
   */
  @Override
  public int available() throws IOException {
    int excessBytes = excessEnd - excessPtr;
    if (excessBytes > 0) {
      return excessBytes;
    }
    if (finished) {
      return 0;
    }
    // FIXME Not very accurate as may include the MAC - or it may not, this is not the
    //  full
    // length of the stream. Maybe we should return 0?
    return in.available();
  }

  /**
   * Skips bytes by decrypting and discarding them. Required for MAC validation during close().
   *
   * @param n Number of bytes to skip
   * @return Actual skipped bytes (maybe less if EOF reached)
   */
  @Override
  public long skip(long n) throws IOException {
    // FIXME unit test skip()
    long skipped = 0L;
    byte[] temp = new byte[excess.length];
    while (n > 0) {
      int availableExcess = excessEnd - excessPtr;
      if (availableExcess > 0) {
        int skipBytes = (int) Math.min(n, availableExcess);
        excessPtr += skipBytes;
        skipped += skipBytes;
        n -= skipBytes;
        if (excessPtr == excessEnd) {
          excessEnd = 0;
          excessPtr = 0;
        }
        continue;
      }
      int read;
      if (n < temp.length) {
        read = read(temp, 0, (int) n);
      } else {
        read = read(temp);
      }
      if (read <= 0) {
        return skipped;
      }
      skipped += read;
      n -= read;
    }
    return skipped;
  }

  /**
   * Closes the stream and enforces full data consumption to validate MAC. <b>Must be called</b> to
   * ensure authentication, even if exceptions occur.
   */
  @Override
  public void close() throws IOException {
    if (!finished) {
      // Must read the rest of the data to check hash integrity.
      //noinspection StatementWithEmptyBody
      while (skip(Long.MAX_VALUE) > 0) {
        // Force read all data
      }
    }
    in.close();
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public void mark(int readLimit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() throws IOException {
    throw new IOException("Mark/reset not supported");
  }

  /**
   * Transfers excess decrypted bytes from previous operations to the target buffer.
   *
   * @return Number of excess bytes copied to buffer
   */
  private int handleExcessBytes(byte[] buf, int offset, int length) {
    int available = excessEnd - excessPtr;
    if (available <= 0) {
      return 0;
    }

    var bytesToCopy = Math.min(length, available);
    System.arraycopy(excess, excessPtr, buf, offset, bytesToCopy);
    excessPtr += bytesToCopy;

    if (excessEnd == excessPtr) {
      excessEnd = 0;
      excessPtr = 0;
    }
    return bytesToCopy;
  }

  /** AEAD cipher instance for decryption and authentication. */
  private final AEADBlockCipher cipher;

  /** Single-byte buffer for {@link #read()} method. */
  private final byte[] oneByte = new byte[1];

  /** Buffer for excess decrypted bytes that couldn't fit in previous read operations. */
  private final byte[] excess;

  /** Flag indicating if the end of stream/MAC verification has been processed. */
  private boolean finished;

  /** End index of valid data in {@link #excess} buffer. */
  private int excessEnd;

  /** Current read position in {@link #excess} buffer. */
  private int excessPtr;
}
