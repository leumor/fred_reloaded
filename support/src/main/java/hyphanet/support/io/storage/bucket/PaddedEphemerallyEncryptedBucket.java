/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.JcaProvider;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.stream.NullInputStream;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A proxy {@link Bucket} which adds:
 *
 * <ul>
 *   <li>Encryption with the supplied cipher, and a random, ephemeral key.
 *   <li>Padding to the next power of 2 (PO2) size.
 * </ul>
 *
 * <p>This class ensures that data written to it is encrypted and padded before being stored in the
 * underlying {@link Bucket}. The encryption uses a randomly generated key and initialization vector
 * (IV) for each instance. Padding is added to prevent size correlation attacks.
 */
public class PaddedEphemerallyEncryptedBucket implements Bucket, Serializable {

  /** Minimum size the padded data must be, even if the original is smaller. */
  public static final int MIN_PADDED_SIZE = 1024;

  /** Magic number used for serialization format verification. */
  public static final int MAGIC = 0x66c71fc9;

  /** Version number for the serialization format. */
  static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;
  private static final Logger logger =
      LoggerFactory.getLogger(PaddedEphemerallyEncryptedBucket.class);

  static {
    Security.addProvider(new JcaProvider());
  }

  /**
   * Create a padded encrypted proxy bucket.
   *
   * @param bucket The bucket which we are proxying to. Must be empty.
   * @param minSize The minimum padded size of the file (after it has been closed).
   * @param strongPRNG A strong pseudo-random number generator we will key from.
   * @param weakPRNG A weak pseudo-random number generator we will pad from.
   *     <p><strong>Serialization Note:</strong> It is not our responsibility to free the random
   *     number generators, but we WILL free the underlying bucket.
   * @throws IllegalArgumentException If the provided bucket is not empty.
   */
  public PaddedEphemerallyEncryptedBucket(
      Bucket bucket, int minSize, RandomGenerator strongPRNG, Random weakPRNG) {
    this.bucket = bucket;
    if (bucket.size() != 0) {
      throw new IllegalArgumentException("Bucket must be empty");
    }
    key = new byte[32];
    randomSeed = new byte[32];
    weakPRNG.nextBytes(randomSeed);
    strongPRNG.nextBytes(key);
    iv = new byte[32];
    strongPRNG.nextBytes(iv);
    minPaddedSize = minSize;
    readOnly = false;
    lastOutputStream = 0;
    dataLength = 0;
  }

  /**
   * Creates a new {@code PaddedEphemerallyEncryptedBucket} as a shallow, read-only copy of an
   * existing bucket. This copy shares the same underlying storage as the original.
   *
   * @param orig The original {@code PaddedEphemerallyEncryptedBucket} to copy.
   * @param newBucket The new underlying {@code Bucket} to use for the copy.
   */
  public PaddedEphemerallyEncryptedBucket(PaddedEphemerallyEncryptedBucket orig, Bucket newBucket) {
    dataLength = orig.dataLength;
    key = orig.key.clone();
    randomSeed = null; // Will be read-only
    readOnly = true;
    bucket = newBucket;
    minPaddedSize = orig.minPaddedSize;
    iv = orig.iv != null ? Arrays.copyOf(orig.iv, 32) : null;
  }

  /** Default constructor for serialization purposes. */
  protected PaddedEphemerallyEncryptedBucket() {
    bucket = null;
    minPaddedSize = 0;
    key = null;
    iv = null;
    randomSeed = null;
  }

  /**
   * Constructs a {@code PaddedEphemerallyEncryptedBucket} by deserializing its state from a {@link
   * DataInputStream}.
   *
   * @param dis The {@link DataInputStream} to read from.
   * @param fg The {@link FilenameGenerator} for creating temporary files.
   * @param persistentFileTracker The {@link PersistentFileTracker} for managing persistent files.
   * @param masterKey The {@link MasterSecret} used for encryption.
   * @throws StorageFormatException If the storage format is invalid.
   * @throws IOException If an I/O error occurs.
   * @throws ResumeFailedException If the bucket cannot be resumed.
   */
  protected PaddedEphemerallyEncryptedBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws StorageFormatException, IOException, ResumeFailedException {
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }
    minPaddedSize = dis.readInt();
    key = new byte[32];
    dis.readFully(key);
    iv = dis.readBoolean() ? dis.readNBytes(32) : null;
    dataLength = dis.readLong();
    readOnly = dis.readBoolean();
    bucket = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
  }

  /**
   * Calculates the padded length of the data, ensuring it is a power of 2 and is not less than
   * {@link #MIN_PADDED_SIZE}.
   *
   * @param dataLength The length of the original data.
   * @param minPaddedSize The minimum size to which the data should be padded.
   * @return The padded length.
   * @throws IllegalStateException if the calculated padded size results in an impossible state.
   */
  public static long paddedLength(long dataLength, long minPaddedSize) {
    long paddedSize = Math.max(dataLength, minPaddedSize);
    if (Long.bitCount(paddedSize) == 1) {
      return paddedSize;
    }
    return Long.highestOneBit(paddedSize) << 1;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned {@link OutputStream} is buffered.
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    return new BufferedOutputStream(getOutputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns an unbuffered {@link OutputStream} for writing data to the Bucket. It initializes
   * the {@code dataLength} to 0. The returned stream is wrapped in {@link
   * PaddedEphemerallyEncryptedOutputStream}.
   */
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    if (readOnly) {
      throw new IOException("Read only");
    }
    OutputStream os = bucket.getOutputStreamUnbuffered();

    synchronized (this) {
      dataLength = 0;
    }
    return new PaddedEphemerallyEncryptedOutputStream(os, ++lastOutputStream);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a buffered {@link InputStream} to read data from the Bucket.
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(getInputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns an unbuffered {@link InputStream} to read data from the Bucket. The stream is
   * wrapped in {@link PaddedEphemerallyEncryptedInputStream} unless the underlying input stream is
   * a {@link NullInputStream}.
   */
  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    InputStream underlying = bucket.getInputStreamUnbuffered();
    return underlying instanceof NullInputStream
        ? new NullInputStream()
        : new PaddedEphemerallyEncryptedInputStream(underlying);
  }

  /**
   * Calculates and returns the padded length of the data.
   *
   * @return The padded length of the data.
   */
  public synchronized long paddedLength() {
    return paddedLength(dataLength, minPaddedSize);
  }

  @Override
  public String getName() {
    return "Encrypted:" + bucket.getName();
  }

  @Override
  public String toString() {
    return super.getClass().getSimpleName() + ':' + bucket;
  }

  @Override
  public synchronized long size() {
    return dataLength;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setReadOnly() {
    readOnly = true;
  }

  /**
   * Returns the underlying {@link Bucket}.
   *
   * @return The underlying {@link Bucket}.
   */
  public Bucket getUnderlying() {
    return bucket;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Disposes of the underlying {@link Bucket}.
   */
  @Override
  public boolean dispose() {
    return bucket.dispose();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the underlying {@link Bucket}.
   */
  @Override
  public void close() {
    bucket.close();
  }

  /**
   * Returns a copy of the decryption key.
   *
   * @return A copy of the decryption key.
   */
  public byte[] getKey() {
    return key.clone();
  }

  @Override
  public Bucket createShadow() {
    Bucket newUnderlying = bucket.createShadow();
    return newUnderlying != null ? new PaddedEphemerallyEncryptedBucket(this, newUnderlying) : null;
  }

  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    randomSeed = new byte[32];
    context.getFastWeakRandom().nextBytes(randomSeed);
    bucket.onResume(context);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Stores the Bucket's reconstruction data to the provided {@link DataOutputStream}. This
   * method writes the magic number, version, minimum padded size, encryption key, IV (if present),
   * data length, read-only flag, and delegates to the underlying bucket.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    dos.writeInt(minPaddedSize);
    dos.write(key);
    dos.writeBoolean(iv != null);
    if (iv != null) {
      dos.write(iv);
    }
    // randomSeed should be recovered in onResume().
    dos.writeLong(dataLength);
    dos.writeBoolean(readOnly);
    bucket.storeTo(dos);
  }

  /**
   * Returns a configured {@link Cipher} instance based on the current encryption settings.
   *
   * @param opMode The operation mode of the cipher (e.g., {@link Cipher#ENCRYPT_MODE} or {@link
   *     Cipher#DECRYPT_MODE}).
   * @return A configured {@link Cipher} instance.
   * @throws IllegalStateException if any of the crypto operations fail due to underlying security
   *     exceptions.
   */
  @SuppressWarnings("java:S3329")
  private synchronized Cipher getCipher(int opMode) {
    try {
      var k = new SecretKeySpec(key, "Rijndael");
      AlgorithmParameterSpec params = iv != null ? new IvParameterSpec(iv) : null;

      var cipher = Cipher.getInstance("RIJNDAEL256/CFB/NoPadding");
      cipher.init(opMode, k, params);
      return cipher;

    } catch (NoSuchAlgorithmException
        | NoSuchPaddingException
        | InvalidKeyException
        | InvalidAlgorithmParameterException e) {
      throw new IllegalStateException("This should never happen", e);
    }
  }

  /**
   * An {@link OutputStream} that encrypts data written to it and pads the output.
   *
   * <p>This class encrypts the data using a cipher and then pads it to the next power of 2 size. It
   * is used internally by {@link PaddedEphemerallyEncryptedBucket} to provide the encryption and
   * padding logic.
   */
  private class PaddedEphemerallyEncryptedOutputStream extends OutputStream {

    /**
     * Constructs a new {@code PaddedEphemerallyEncryptedOutputStream}.
     *
     * @param out The underlying output stream to write to.
     * @param streamNumber The stream number of this output stream to differentiate between
     *     different streams.
     */
    public PaddedEphemerallyEncryptedOutputStream(OutputStream out, int streamNumber) {
      this.out = out;
      dataLength = 0;
      this.streamNumber = streamNumber;
      cipher = getCipher(Cipher.ENCRYPT_MODE);
      int blockSize = cipher.getBlockSize();
      this.inputBuffer =
          new byte[blockSize > 0 ? blockSize : 32]; // Default to 32 if blockSize is not positive
      this.inputBufferPos = 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes a single byte to the output stream, encrypting and buffering as needed.
     */
    @Override
    public void write(int b) throws IOException {
      synchronized (PaddedEphemerallyEncryptedBucket.this) {
        ensureOpen();

        inputBuffer[inputBufferPos++] = (byte) b;
        if (inputBufferPos == inputBuffer.length) {
          flushBuffer();
        }
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes a portion of a byte array to the output stream, encrypting and buffering as needed.
     * It ensures that data is written in blocks suitable for encryption.
     */
    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
      Objects.checkFromIndexSize(offset, length, buf.length);
      if (length == 0) {
        return;
      }

      synchronized (PaddedEphemerallyEncryptedBucket.this) {
        ensureOpen();

        int remainingInputBufferSpace = inputBuffer.length - inputBufferPos;

        if (length >= remainingInputBufferSpace) {
          // Fill the input buffer first
          System.arraycopy(buf, offset, inputBuffer, inputBufferPos, remainingInputBufferSpace);
          inputBufferPos += remainingInputBufferSpace;
          flushBuffer(); // Encrypt and write the full buffer

          // Now process the rest of the input in blocks if possible
          int remainingDataLen = length - remainingInputBufferSpace;
          int blockCount = remainingDataLen / inputBuffer.length;
          int remainingBytesAfterBlocks = remainingDataLen % inputBuffer.length;

          for (int i = 0; i < blockCount; i++) {
            int pos = offset + remainingInputBufferSpace + i * inputBuffer.length;
            encryptAndWrite(buf, pos, inputBuffer.length);
          }

          // Buffer any remaining bytes that are less than a full block
          if (remainingBytesAfterBlocks > 0) {
            System.arraycopy(
                buf,
                offset + remainingInputBufferSpace + blockCount * inputBuffer.length,
                inputBuffer,
                0,
                remainingBytesAfterBlocks);
            inputBufferPos = remainingBytesAfterBlocks;
          }
        } else {
          // Input data fits within the remaining space in the input buffer
          System.arraycopy(buf, offset, inputBuffer, inputBufferPos, length);
          inputBufferPos += length;
        }
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes this output stream, encrypting and padding the remainder data. It writes any
     * remaining data in the buffer and pads the output to the correct padded length.
     */
    @Override
    public void close() throws IOException {
      synchronized (PaddedEphemerallyEncryptedBucket.this) {
        if (closed) return;
        if (streamNumber != lastOutputStream) {
          logger.info("Not padding out to length because have been superseded: {}", getName());
          return;
        }
        var rng = RandomGenerator.getDefault();
        try {
          flushBuffer(); // Flush any remaining data in inputBuffer

          byte[] finalEncryptedOutput;
          try {
            finalEncryptedOutput = cipher.doFinal(); // Finalize encryption
          } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IOException("Error finalizing encryption", e);
          }

          // Modified part: Call encryptAndWrite with the final output
          if (finalEncryptedOutput != null && finalEncryptedOutput.length > 0) {
            encryptAndWrite(finalEncryptedOutput, 0, finalEncryptedOutput.length);
          }

          // Write random padding
          long paddingNeeded = paddedLength() - dataLength;
          int sz = 65536;
          if (paddingNeeded < sz) {
            sz = (int) paddingNeeded;
          }
          byte[] padBuffer = new byte[sz];
          while (paddingNeeded > 0) {
            int chunk = (int) Math.min(paddingNeeded, padBuffer.length);
            rng.nextBytes(padBuffer);
            out.write(padBuffer, 0, chunk);
            paddingNeeded -= chunk;
          }

        } finally {
          closed = true;
          out.close();
        }
      }
    }

    /**
     * Checks if the stream is open. Throws an {@link IOException} if the stream is closed.
     *
     * @throws IOException If the stream is closed.
     */
    private void ensureOpen() throws IOException {
      if (closed || streamNumber != lastOutputStream) {
        throw new IOException("Stream closed or superseded");
      }
    }

    /**
     * Flushes the internal buffer of data by encrypting its content and writing it to the
     * underlying stream.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void flushBuffer() throws IOException {
      if (inputBufferPos > 0) {
        encryptAndWrite(inputBuffer, 0, inputBufferPos);
        inputBufferPos = 0;
      }
    }

    /**
     * Encrypts and writes data to the underlying output stream, updating the data length.
     *
     * @param buffer The buffer containing the data.
     * @param offset The starting offset in the buffer.
     * @param length The number of bytes to write.
     * @throws IOException If an I/O error occurs.
     */
    private void encryptAndWrite(byte[] buffer, int offset, int length) throws IOException {
      byte[] encryptedOutput = cipher.update(buffer, offset, length);
      if (encryptedOutput != null && encryptedOutput.length > 0) {
        out.write(encryptedOutput);
        dataLength += encryptedOutput.length;
      }
    }

    /** The encryption {@link Cipher}. */
    final Cipher cipher;

    /** The underlying {@link OutputStream}. */
    final OutputStream out;

    /** The stream number of this output stream. */
    final int streamNumber;

    /** The buffer used for collecting data before encryption. */
    private final byte[] inputBuffer;

    /** The current position in the input buffer. */
    private int inputBufferPos;

    /** A flag indicating whether the output stream is closed. */
    private boolean closed;
  }

  /**
   * An {@link InputStream} that decrypts data read from it.
   *
   * <p>This class decrypts the data read from the underlying input stream using the cipher provided
   * in {@link PaddedEphemerallyEncryptedBucket}. It buffers the input and output of the decryption
   * to optimize the read operations.
   */
  private class PaddedEphemerallyEncryptedInputStream extends InputStream {

    /**
     * Constructs a new {@code PaddedEphemerallyEncryptedInputStream}.
     *
     * @param in The underlying {@link InputStream} to read from.
     */
    public PaddedEphemerallyEncryptedInputStream(InputStream in) {
      this.in = in;
      cipher = getCipher(Cipher.DECRYPT_MODE);
      int blockSize = cipher.getBlockSize();
      inputBuffer =
          new byte[blockSize > 0 ? blockSize : 32]; // Default to 32 if blockSize is not positive
      outputBuffer =
          new byte[blockSize > 0 ? blockSize * 2 : 64]; // Double block size for output buffer
      outputBufferPos = 0;
      outputBufferCount = 0;
      totalRead = 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the next byte of decrypted data from the input stream. It fetches encrypted data
     * from the underlying input stream, decrypts it, and provides the decrypted bytes through an
     * internal buffer.
     */
    @Override
    public int read() throws IOException {
      if (outputBufferPos < outputBufferCount) {
        totalRead++;
        return outputBuffer[outputBufferPos++] & 0xFF; // Return byte from buffer
      }

      if (totalRead >= dataLength || eofReached && finalBlockProcessed) {
        return -1; // End of stream
      }

      outputBufferPos = 0;
      outputBufferCount = 0;

      int bytesRead = in.read(inputBuffer);
      if (bytesRead == -1) {
        eofReached = true;
        try {
          outputBufferCount = cipher.doFinal(outputBuffer, 0); // Finalize
          finalBlockProcessed = true;
        } catch (ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
          throw new IOException("Decryption error", e);
        }
        return outputBufferCount > 0 ? outputBuffer[outputBufferPos++] & 0xFF : -1;
      } else {
        try {
          outputBufferCount = cipher.update(inputBuffer, 0, bytesRead, outputBuffer, 0);
        } catch (ShortBufferException e) {
          throw new IOException("Decryption buffer error", e);
        }
        return outputBufferCount > 0
            ? outputBuffer[outputBufferPos++] & 0xFF
            :
            // Try to read again (could be EOF or more input needed)
            // This might happen if cipher.update produces no output for input.
            // In CFB, update should generally produce output, but handle just in case.
            read();
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads up to {@code len} bytes of decrypted data from the input stream into the provided
     * byte array. It decrypts data as needed from the underlying stream to fill the byte array.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      Objects.checkFromIndexSize(off, len, b.length);
      int x = available();
      if (x <= 0) return -1;

      int bytesReadTotal = 0;
      while (bytesReadTotal < len) {
        int nextByte = read();
        if (nextByte == -1) {
          if (bytesReadTotal == 0) {
            return -1; // EOF at start
          } else {
            return bytesReadTotal; // EOF after reading some bytes
          }
        }
        b[off + bytesReadTotal] = (byte) nextByte;
        bytesReadTotal++;
      }
      totalRead += bytesReadTotal;
      return bytesReadTotal;
    }

    @Override
    public final int available() {
      int x = (int) Math.min(dataLength - totalRead, Integer.MAX_VALUE);
      return Math.max(x, 0);
    }

    @Override
    public long skip(long bytes) throws IOException {
      byte[] buf = new byte[(int) Math.min(4096, bytes)];
      long skipped = 0;
      while (skipped < bytes) {
        int x = read(buf, 0, (int) Math.min(bytes - skipped, buf.length));
        if (x <= 0) {
          return skipped;
        }
        skipped += x;
      }
      return skipped;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    /** The underlying {@link InputStream}. */
    final InputStream in;

    /** The decryption {@link Cipher}. */
    final Cipher cipher;

    /** The buffer for data read from the underlying stream. */
    private final byte[] inputBuffer; // Buffer for data from underlying stream

    /** The buffer for decrypted data. */
    private final byte[] outputBuffer; // Buffer for encrypted data

    /** Total number of bytes read from the stream. */
    long totalRead;

    /** The current position in the output buffer. */
    private int outputBufferPos;

    /** The count of bytes available in the output buffer. */
    private int outputBufferCount;

    /** Flag indicating if the end of the underlying stream is reached. */
    private boolean eofReached = false;

    /** Flag indicating if the final block of data has been processed. */
    private boolean finalBlockProcessed = false;
  }

  /** The underlying bucket to which data is written. */
  private final Bucket bucket;

  /** The minimum padded size to which data must be padded. */
  private final int minPaddedSize;

  /** The decryption key. */
  private final byte[] key;

  /** The initialization vector (IV) used for encryption. */
  private final byte[] iv;

  /** A transient seed for random number generation after deserialization. */
  private transient byte[] randomSeed;

  /** Length of the non-padded (valid) data. */
  private long dataLength;

  /** A flag indicating whether the bucket is read-only. */
  private boolean readOnly;

  /** Tracks the last output stream number to close in a safe way */
  private transient int lastOutputStream;
}
