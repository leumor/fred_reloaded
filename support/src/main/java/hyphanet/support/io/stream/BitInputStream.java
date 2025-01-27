package hyphanet.support.io.stream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Provides methods for reading bits from an {@link InputStream}.
 *
 * <p>This class allows reading data from an input stream at the bit level, rather than byte level.
 * It supports specifying the bit order ({@link ByteOrder#BIG_ENDIAN} or {@link
 * ByteOrder#LITTLE_ENDIAN}) for interpreting multi-bit values.
 *
 * <p>Internally, it buffers bytes read from the underlying {@link InputStream} and provides methods
 * to extract bits from this buffer.
 *
 * <p><b>Note:</b> This class does not extend {@link InputStream} because it operates at the bit
 * level, which is a different abstraction than the byte-oriented operations of {@link InputStream}.
 * Extending {@link InputStream} would imply byte-level read operations, which is not the intended
 * behavior of this class.
 */
public class BitInputStream implements AutoCloseable {

  /**
   * Constructs a {@code BitInputStream} with the specified input stream, using {@link
   * ByteOrder#BIG_ENDIAN} as the default bit order.
   *
   * @param in the underlying {@link InputStream} to read from. Must not be null.
   */
  public BitInputStream(InputStream in) {
    this(in, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Constructs a {@code BitInputStream} with the specified input stream and bit order.
   *
   * @param in the underlying {@link InputStream} to read from. Must not be null.
   * @param bitOrder the {@link ByteOrder} to use when reading multi-bit values. Must not be null.
   */
  public BitInputStream(InputStream in, ByteOrder bitOrder) {
    this.in = in;
    streamBitOrder = bitOrder;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes this bit input stream and releases any system resources associated with it.
   *
   * <p>This method will also close the underlying input stream.
   *
   * @throws IOException if an I/O error occurs.
   */
  @Override
  public void close() throws IOException {
    in.close();
  }

  /**
   * Reads a single bit from the input stream.
   *
   * <p>Bits are read according to the {@link #streamBitOrder} specified in the constructor. If
   * {@link ByteOrder#BIG_ENDIAN} is used, bits are read from most significant to least significant
   * within each byte. If {@link ByteOrder#LITTLE_ENDIAN} is used, it reads bits from least
   * significant to most significant within each byte.
   *
   * <p>If the internal buffer is empty, a new byte is read from the underlying input stream.
   *
   * @return 0 or 1 representing the bit value.
   * @throws IOException if an I/O error occurs.
   * @throws EOFException if the end of the underlying input stream is reached before a bit can be
   *     read.
   */
  public int readBit() throws IOException {
    if (bitsLeft == 0) {
      bitsBuffer = in.read();
      if (bitsBuffer < 0) {
        throw new EOFException();
      }
      bitsLeft = 8;
    }
    int bitIdx = (streamBitOrder == ByteOrder.BIG_ENDIAN ? --bitsLeft : 8 - bitsLeft--);
    return (bitsBuffer >> bitIdx) & 1;
  }

  /**
   * Reads an integer value of the specified length in bits using the default {@link
   * #streamBitOrder}.
   *
   * @param length the number of bits to read for the integer value. Must be positive.
   * @return the integer value read from the stream.
   * @throws IOException if an I/O error occurs.
   * @throws EOFException if the end of the underlying input stream is reached before all bits can
   *     be read.
   * @throws IllegalArgumentException if {@code length} is not positive.
   */
  public int readInt(int length) throws IOException {
    return readInt(length, streamBitOrder);
  }

  /**
   * Reads an integer value of the specified length in bits using the given {@link ByteOrder}.
   *
   * <p>This method optimizes reading byte-aligned integer values (lengths that are multiples of 8)
   * when the internal bit buffer is empty by directly reading bytes from the underlying input
   * stream. For non-byte-aligned lengths or when bits are remaining in the buffer, it uses a slower
   * bit-by-bit reading approach.
   *
   * @param length the number of bits to read for the integer value. Must be positive.
   * @param bitOrder the {@link ByteOrder} to use when reading the integer value.
   * @return the integer value read from the stream.
   * @throws IOException if an I/O error occurs.
   * @throws EOFException if the end of the underlying input stream is reached before all bits can
   *     be read.
   * @throws IllegalArgumentException if {@code length} is not positive.
   * @implSpec The method first checks if a fast path can be taken for byte-aligned reads. If {@code
   *     bitsLeft == 0 && length <= 32 && length % 8 == 0}, it calls {@link #readByteAlignedInt(int,
   *     ByteOrder)} for optimization. Otherwise, it uses the slower bit-by-bit reading approach in
   *     {@link #readIntSlowPath(int, ByteOrder)}.
   */
  public int readInt(int length, ByteOrder bitOrder) throws IOException {
    if (length == 0) {
      return 0;
    }

    if (length < 0) {
      throw new IllegalArgumentException("Invalid length: " + length + " (must be positive)");
    }

    if (bitsLeft == 0 && length <= 32 && length % 8 == 0) {
      return readByteAlignedInt(length, bitOrder);
    } else {
      return readIntSlowPath(length, bitOrder);
    }
  }

  /**
   * Reads exactly {@code b.length} bytes from the input stream and stores them into the byte array
   * {@code b}.
   *
   * <p>If the internal bit buffer is empty (i.e., byte-aligned), this method attempts to read
   * directly from the underlying input stream for optimal performance. Otherwise, it reads byte by
   * byte using {@link #readInt(int)} to ensure correct bit handling.
   *
   * @param b the byte array to read bytes into.
   * @throws IOException if an I/O error occurs.
   * @throws EOFException if the end of the underlying input stream is reached before {@code
   *     b.length} bytes could be read.
   */
  public void readFully(byte[] b) throws IOException {
    if (bitsLeft == 0) {
      if (in.read(b) < b.length) {
        throw new EOFException();
      }
      return;
    }

    for (int i = 0; i < b.length; i++) {
      b[i] = (byte) readInt(8);
    }
  }

  /**
   * Skips over and discards {@code n} bits of data from this input stream.
   *
   * <p>This method attempts to skip bits efficiently by first consuming any remaining bits in the
   * internal buffer, then skipping bytes directly from the underlying input stream if possible, and
   * finally skipping bit by bit if necessary.
   *
   * @param n the number of bits to skip. Must be non-negative.
   * @return the actual number of bits skipped, which may be less than {@code n} if the end of the
   *     input stream is reached.
   * @throws IOException if an I/O error occurs.
   */
  public long skip(long n) throws IOException {
    if (n <= 0) {
      return 0;
    }

    long remaining = n;

    if (bitsLeft > 0) {
      if (bitsLeft > remaining) {
        readInt((int) remaining);
        return remaining;
      } else {
        remaining -= bitsLeft;
        readInt(bitsLeft);
      }
    }

    while (remaining >= 8) {
      if (in.read() == -1) {
        return n - remaining;
      }

      remaining -= 8;
    }

    while (remaining > 0) {
      try {
        readBit();
        remaining--;
      } catch (EOFException ignored) {
        return n - remaining;
      }
    }

    return remaining;
  }

  /**
   * Optimized method to read byte-aligned integer values directly from the input stream.
   *
   * <p>This method is intended for internal use and is called by {@link #readInt(int, ByteOrder)}
   * when a fast byte-aligned read is possible. It reads 1, 2, 3, or 4 bytes based on the {@code
   * length} (8, 16, 24, or 32 bits respectively) and assembles them into an integer according to
   * the specified {@code bitOrder}.
   *
   * @param length the number of bits to read (must be 8, 16, 24, or 32).
   * @param bitOrder the {@link ByteOrder} to use when reading the integer value.
   * @return the integer value read from the stream.
   * @throws IOException if an I/O error occurs.
   * @throws EOFException if the end of the underlying input stream is reached before all bytes
   *     could be read.
   * @throws IllegalArgumentException if {@code length} is not 8, 16, 24, or 32.
   * @implSpec The method uses a switch statement to handle different byte lengths (8, 16, 24, 32
   *     bits). For each case, it reads the required number of bytes from the underlying input
   *     stream and combines them into an integer value based on the provided {@code bitOrder}.
   */
  private int readByteAlignedInt(int length, ByteOrder bitOrder) throws IOException {
    return switch (length) {
      case 8 -> {
        int b = in.read();
        if (b < 0) {
          throw new EOFException();
        }
        yield b;
      }
      case 16 -> {
        int b = in.read();
        int b2 = in.read();
        if ((b | b2) < 0) {
          throw new EOFException();
        }
        yield (bitOrder == ByteOrder.BIG_ENDIAN) ? (b << 8 | b2) : (b | b2 << 8);
      }
      case 24 -> {
        int b = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b | b2 | b3) < 0) {
          throw new EOFException();
        }
        yield (bitOrder == ByteOrder.BIG_ENDIAN)
            ? (b << 16 | b2 << 8 | b3)
            : (b | b2 << 8 | b3 << 16);
      }
      case 32 -> {
        int b = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b | b2 | b3 | b4) < 0) {
          throw new EOFException();
        }
        yield (bitOrder == ByteOrder.BIG_ENDIAN)
            ? (b << 24 | b2 << 16 | b3 << 8 | b4)
            : (b | b2 << 8 | b3 << 16 | b4 << 24);
      }
      default -> readIntSlowPath(length, bitOrder);
    };
  }

  /**
   * Slow path method to read integer values bit by bit from the input stream.
   *
   * <p>This method is used by {@link #readInt(int, ByteOrder)} when a fast byte-aligned read is not
   * possible. It reads bits one by one using {@link #readBit()} and assembles them into an integer
   * value according to the specified {@code bitOrder}.
   *
   * @param length the number of bits to read for the integer value.
   * @param bitOrder the {@link ByteOrder} to use when reading the integer value.
   * @return the integer value read from the stream.
   * @throws IOException if an I/O error occurs.
   * @throws EOFException if the end of the underlying input stream is reached before all bits could
   *     be read.
   * @implSpec The method iterates {@code length} times, reading one bit in each iteration using
   *     {@link #readBit()}. In each iteration, it shifts the accumulated {@code value} and adds the
   *     newly read bit, respecting the specified {@code bitOrder} for bits arrangement. For {@link
   *     ByteOrder#BIG_ENDIAN}, bits are added from most significant to least significant. For
   *     {@link ByteOrder#LITTLE_ENDIAN}, bits are added from least significant to most significant.
   */
  private int readIntSlowPath(int length, ByteOrder bitOrder) throws IOException {
    int value = 0;
    if (bitOrder == ByteOrder.BIG_ENDIAN) {
      for (int i = 0; i < length; i++) {
        value = value << 1 | readBit();
      }
    } else {
      for (int i = 0; i < length; i++) {
        value |= readBit() << i;
      }
    }
    return value;
  }

  /** The underlying input stream to read bytes from. */
  private final InputStream in;

  /** The bit order to use when reading multi-bit values from the stream. */
  private final ByteOrder streamBitOrder;

  /**
   * Buffer to hold the current byte being read from the input stream. Bits are extracted from this
   * buffer.
   */
  private int bitsBuffer;

  /** Number of bits remaining in the {@link #bitsBuffer}. Ranges from 0 to 8. */
  private byte bitsLeft;
}
