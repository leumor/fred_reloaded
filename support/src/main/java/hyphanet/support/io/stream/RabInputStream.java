package hyphanet.support.io.stream;

import hyphanet.support.io.randomaccessbuffer.RandomAccessBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An {@link InputStream} that reads data from a {@link RandomAccessBuffer}. This class allows
 * treating a portion of a {@link RandomAccessBuffer} as a sequential input stream. It provides
 * methods to read bytes from the underlying buffer, starting from a specified offset and up to a
 * given size.
 */
public class RabInputStream extends InputStream {

  /**
   * Constructs a {@link RabInputStream} that reads from the given {@link RandomAccessBuffer}.
   *
   * @param data The underlying {@link RandomAccessBuffer} to read from. Must not be {@code null}.
   * @param offset The starting offset within the {@code data} buffer from where reading should
   *     begin. Must be non-negative.
   * @param size The maximum number of bytes that can be read from the {@code data} buffer, starting
   *     from the {@code offset}. Must be non-negative.
   * @throws IndexOutOfBoundsException if {@code offset} or {@code size} is negative, or if {@code
   *     offset + size} exceeds the buffer's capacity.
   */
  public RabInputStream(RandomAccessBuffer data, long offset, long size) {
    Objects.checkFromIndexSize(offset, size, data.size());
    underlying = data;
    rabOffset = offset;
    rabLength = size;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation reads a single byte from the underlying {@link RandomAccessBuffer}. It
   * delegates to the {@link #read(byte[], int, int)} method, using a temporary single-byte buffer.
   */
  @Override
  public int read() throws IOException {
    int bytesRead = read(oneByte, 0, 1);
    return bytesRead == -1
        ? -1 // EOF
        : oneByte[0] & 0xFF;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reads up to {@code length} bytes of data from the underlying {@link RandomAccessBuffer} into
   * an array of bytes. An attempt is made to read as many as {@code length} bytes, but a smaller
   * number may be read, possibly zero. The number of bytes actually read is returned as an integer.
   *
   * <p>If {@code rabOffset} is greater than or equal to {@code rabLength}, then EOF is reached and
   * {@code -1} is returned. If {@code length} is zero, then no bytes are read and {@code 0} is
   * returned. Otherwise, the method calculates the number of bytes to read, which is the minimum of
   * {@code length} and the remaining bytes in the stream ({@code rabLength - rabOffset}). It then
   * uses the {@link RandomAccessBuffer#pread(long, byte[], int, int)} method to read data from the
   * underlying buffer at the current {@code rabOffset}, into the provided buffer {@code buf} at the
   * specified {@code offset}. Finally, it updates the {@code rabOffset} by the number of bytes read
   * and returns the number of bytes read.
   *
   * @throws IndexOutOfBoundsException if {@code offset} is negative, {@code length} is negative, or
   *     {@code offset + length} is greater than the length of the array {@code buf}.
   */
  @Override
  public int read(byte[] buf, int offset, int length) throws IOException {
    Objects.checkFromIndexSize(offset, length, buf.length);
    if (rabOffset >= rabLength) {
      return -1; // Indicate EOF by returning -1 as per InputStream contract
    }
    if (length <= 0) {
      return 0; // No bytes requested to read
    }

    int bytesToRead = (int) Math.min(length, rabLength - rabOffset);
    if (bytesToRead <= 0) {
      return -1; // Should not reach here normally, but just in case, return EOF
    }

    underlying.pread(rabOffset, buf, offset, bytesToRead);
    rabOffset += bytesToRead;
    return bytesToRead;
  }

  /**
   * The underlying {@link RandomAccessBuffer} from which this {@link InputStream} reads data. This
   * buffer provides random access capabilities, but this stream reads it sequentially.
   */
  private final RandomAccessBuffer underlying;

  /** A single-byte buffer used by the {@link #read()} method to avoid repeated allocation. */
  private final byte[] oneByte = new byte[1];

  /**
   * The total number of bytes available to read from the underlying {@link RandomAccessBuffer}.
   * This is the maximum size of the stream.
   */
  private final long rabLength;

  /**
   * The current read offset within the underlying {@link RandomAccessBuffer}. This offset is
   * incremented as bytes are read from the stream.
   */
  private long rabOffset;
}
