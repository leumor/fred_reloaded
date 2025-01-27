/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.stream;

import java.io.*;

/**
 * Utilities for manipulating headers on streams.
 *
 * <p>Provides methods to create streams that transparently prepend or consume headers. This is
 * useful for protocols that use headers to provide metadata about the stream content.
 *
 * <p>The class is designed to be used with {@link InputStream} and {@link OutputStream} to add
 * header handling capabilities.
 *
 * @author infinity0
 */
public final class HeaderStreams {

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * <p>This class is designed to be used statically and should not be instantiated.
   */
  private HeaderStreams() {}

  /**
   * Create an {@link InputStream} which transparently attaches an extra header to the underlying
   * stream.
   *
   * <p>This method wraps the provided {@link InputStream} with a {@link FilterInputStream} that
   * prepends the given header bytes before reading from the original stream.
   *
   * @param hd The header bytes to prepend to the stream.
   * @param s The underlying {@link InputStream} to read from after the header.
   * @return A new {@link InputStream} that first reads the header and then the underlying stream.
   * @throws IOException if an I/O error occurs.
   */
  public static InputStream augInput(final byte[] hd, InputStream s) throws IOException {
    return new FilterInputStream(s) {
      /**
       * {@inheritDoc}
       *
       * @return The number of bytes that can be read from this input stream without blocking. This
       *     includes the remaining bytes in the header and the available bytes in the underlying
       *     stream.
       */
      @Override
      public int available() throws IOException {
        return (hd.length - i) + in.available();
      }

      /**
       * {@inheritDoc}
       *
       * <p>Reads the next byte of data from this input stream. It first reads from the header bytes
       * until they are exhausted, then reads from the underlying input stream.
       *
       * @implNote If there are still bytes left in the header ({@code i < hd.length}), it returns
       *     the next byte from the header array {@code hd} and increments the index {@code i}.
       *     Otherwise, it delegates the read operation to the underlying input stream {@code in}.
       */
      @Override
      public int read() throws IOException {
        return (i < hd.length) ? (hd[i++] & 0xFF) : in.read();
      }

      /**
       * {@inheritDoc}
       *
       * <p>Reads up to {@code len} bytes of data from this input stream into an array of bytes. It
       * first reads from the header bytes until they are exhausted, then reads from the underlying
       * input stream.
       *
       * @implNote This method first calculates how many bytes can be read from the header ({@code
       *     headerBytes}). If there are header bytes available, it copies them to the buffer {@code
       *     buf} and updates the indices and lengths accordingly. Then, if there is still space
       *     left in the buffer ({@code len > 0}), it attempts to read from the underlying input
       *     stream {@code in}. The method returns the total number of bytes read, which is the sum
       *     of bytes read from the header and the underlying stream. It handles the case where the
       *     underlying stream returns {@code -1} (end of stream) correctly.
       */
      @Override
      public int read(byte[] buf, int off, int len) throws IOException {
        if (len == 0) {
          return 0;
        }
        int prev = i;
        int headerBytes = Math.min(hd.length - i, len);
        if (headerBytes > 0) {
          System.arraycopy(hd, i, buf, off, headerBytes);
          i += headerBytes;
          off += headerBytes;
          len -= headerBytes;
        }
        int headerRead = i - prev;
        if (len <= 0) {
          return headerRead;
        }
        int streamRead = in.read(buf, off, len);
        if (streamRead == -1) {
          return headerRead > 0 ? headerRead : -1;
        }
        return headerRead + streamRead;
      }

      /**
       * {@inheritDoc}
       *
       * <p>Skips over and discards {@code len} bytes of data from this input stream. It first skips
       * bytes from the header if available, then skips from the underlying input stream.
       *
       * @implNote This method first calculates how many bytes can be skipped from the header
       *     ({@code headerSkip}). It updates the header index {@code i} and reduces the remaining
       *     length {@code len}. Then, it delegates the skip operation for the remaining length to
       *     the underlying input stream {@code in}. The method returns the total number of bytes
       *     skipped, which is the sum of bytes skipped from the header and the underlying stream.
       */
      @Override
      public long skip(long len) throws IOException {
        if (len <= 0) {
          return 0;
        }

        long headerSkip = Math.min((long) hd.length - i, len);
        i += (int) headerSkip;
        len -= headerSkip;
        long streamSkip = in.skip(len);
        return headerSkip + streamSkip;
      }

      /**
       * {@inheritDoc}
       *
       * @return Always {@code false} as mark and reset are not supported by this implementation.
       */
      @Override
      public boolean markSupported() {
        // TODO LOW
        return false;
      }

      /**
       * {@inheritDoc}
       *
       * <p>This operation is not supported and does nothing.
       */
      @Override
      public void mark(int limit) {
        // TODO LOW
      }

      /**
       * {@inheritDoc}
       *
       * @throws IOException always, because mark and reset are not supported.
       */
      @Override
      public void reset() throws IOException {
        // TODO LOW
        throw new IOException("mark/reset not supported");
      }

      /**
       * Index of next byte to read from the header array {@code hd}.
       *
       * <p>This field keeps track of the current position within the header byte array during
       * reading. It is incremented as bytes from the header are read. Once {@code i} reaches the
       * length of {@code hd}, subsequent reads will be from the underlying input stream.
       */
      private int i = 0;
    };
  }

  /**
   * Create an {@link OutputStream} which transparently swallows the expected header written to the
   * underlying stream.
   *
   * <p>This method wraps the provided {@link OutputStream} with a {@link FilterOutputStream} that
   * expects to receive the given header bytes first. If the written bytes do not match the header,
   * an {@link IOException} is thrown. After the header is consumed, all subsequent writes are
   * passed through to the underlying output stream.
   *
   * @param hd The header bytes expected to be written first.
   * @param s The underlying {@link OutputStream} to write to after the header is consumed.
   * @return A new {@link OutputStream} that consumes the header and then writes to the underlying
   *     stream.
   */
  public static OutputStream dimOutput(final byte[] hd, OutputStream s) {
    return new FilterOutputStream(s) {
      /**
       * {@inheritDoc}
       *
       * <p>Writes the specified byte to this output stream. If header bytes are still expected, it
       * checks if the written byte matches the expected header byte. If it does not match, an
       * {@link IOException} is thrown. Otherwise, if the header has been fully consumed, the byte
       * is written to the underlying output stream.
       *
       * @throws IOException if an I/O error occurs or if the written byte does not match the
       *     expected header byte.
       * @implNote If there are still header bytes expected ({@code i < hd.length}), it compares the
       *     written byte {@code b} with the expected header byte {@code hd[i]}. If they do not
       *     match, it throws an {@link IOException}. If they match, it increments the header index
       *     {@code i}. Once all header bytes have been processed ({@code i >= hd.length}), it
       *     delegates the write operation to the underlying output stream {@code out}.
       */
      @Override
      public void write(int b) throws IOException {
        if (i < hd.length) {
          if ((byte) b != hd[i]) {
            throw new IOException("byte " + i + ": expected '" + hd[i] + "'; got '" + b + "'.");
          }
          i++;
        } else {
          out.write(b);
        }
      }

      /**
       * {@inheritDoc}
       *
       * <p>Writes {@code len} bytes from the specified byte array starting at offset {@code off} to
       * this output stream. It first checks and consumes the expected header bytes from the input
       * buffer. If any of the bytes in the buffer meant to be header bytes do not match, an {@link
       * IOException} is thrown. After the header is consumed, the remaining bytes (if any) are
       * written to the underlying output stream.
       *
       * @throws IOException if an I/O error occurs or if the written bytes (intended as header) do
       *     not match the expected header bytes.
       * @implNote This method iterates through the input buffer {@code buf} as long as there are
       *     expected header bytes remaining ({@code i < hd.length}) and there are bytes left to
       *     process in the buffer ({@code len > 0}). For each byte, it compares it with the
       *     expected header byte {@code hd[i]}. If they do not match, it throws an {@link
       *     IOException}. If they match, it increments the header index {@code i} and moves to the
       *     next byte in the buffer. After processing the header part (or if no header is expected
       *     anymore), it writes the remaining bytes from the buffer (starting from the updated
       *     offset {@code off} and length {@code len}) to the underlying output stream {@code out}.
       */
      @Override
      public void write(byte[] buf, int off, int len) throws IOException {
        for (; i < hd.length && len > 0; i++, len--, off++) {
          if (buf[off] != hd[i]) {
            throw new IOException(
                "byte " + i + ": expected '" + hd[i] + "'; got '" + buf[off] + "'.");
          }
        }
        out.write(buf, off, len);
      }

      /**
       * Index of next byte to expect from the header array {@code hd}.
       *
       * <p>This field keeps track of the current position within the header byte array during
       * writing. It is incremented as bytes are successfully matched against the expected header.
       * Once {@code i} reaches the length of {@code hd}, subsequent writes are directly passed to
       * the underlying output stream.
       */
      private int i = 0;
    };
  }
}
