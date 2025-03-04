package hyphanet.support.io.util;

import hyphanet.crypt.Global;
import hyphanet.support.io.stream.LineReadingInputStream;
import hyphanet.support.io.stream.ZeroInputStream;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import org.bouncycastle.crypto.DefaultBufferedBlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class providing stream and file operations with enhanced reliability and security.
 *
 * <p>This class offers methods for:
 *
 * <ul>
 *   <li>Reading and writing files with UTF-8 encoding
 *   <li>Stream manipulation and copying
 *   <li>Secure log file reading
 *   <li>Buffer management for optimal I/O operations
 * </ul>
 *
 * <p>All operations are designed to be atomic where possible and include proper resource management
 * and error handling.
 */
public final class Stream {

  /**
   * Default buffer size used for file operations.
   *
   * <p>Set to 32KB (32,768 bytes) for optimal performance on most modern file systems.
   *
   * <p>This value has been chosen based on empirical testing across different platforms and
   * represents a balance between memory usage and I/O performance.
   */
  public static final int BUFFER_SIZE = 32 * 1024;

  /**
   * Reusable zero-filled input stream instance.
   *
   * <p>Used for padding operations and security-related tasks.
   */
  private static final ZeroInputStream zis = new ZeroInputStream();

  private static final Logger logger = LoggerFactory.getLogger(Stream.class);

  /**
   * Cipher input stream for encrypted operations.
   *
   * <p>Maintains state for ongoing encryption/decryption operations.
   */
  private static @Nullable CipherInputStream cis;

  /**
   * Counter for tracking cipher input stream operations.
   *
   * <p>Used to ensure proper sequencing of encryption/decryption operations.
   */
  private static long cisCounter;

  private Stream() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Reads the entire content of a file as UTF-8 and returns it as a StringBuilder. This is a
   * convenience method that delegates to {@link #readUTF(Path)}.
   *
   * @param file The file to read the content from
   * @return A StringBuilder containing the file's content encoded as UTF-8
   * @throws FileNotFoundException if the file does not exist or cannot be opened for reading
   * @throws IOException if an I/O error occurs during reading
   * @see #readUTF(Path)
   */
  public static StringBuilder readUTF(File file) throws IOException {
    return readUTF(file, 0);
  }

  /**
   * Reads the content of a file as UTF-8, starting at a specified offset. This is a convenience
   * method that delegates to {@link #readUTF(Path, long)}.
   *
   * @param file The file to read the content from
   * @param offset The byte offset in the file at which to start reading
   * @return A StringBuilder containing the file's content from the specified offset, encoded as
   *     UTF-8
   * @throws FileNotFoundException if the file does not exist or cannot be opened for reading
   * @throws IOException if an I/O error occurs during reading
   * @throws IllegalArgumentException if offset is negative
   * @see #readUTF(Path, long)
   */
  public static StringBuilder readUTF(File file, long offset) throws IOException {
    return readUTF(file.toPath(), offset);
  }

  /**
   * Reads the entire content of a file as UTF-8 using NIO Path API. This is a convenience method
   * that delegates to {@link #readUTF(Path, long)}.
   *
   * @param path The path to the file to read
   * @return A StringBuilder containing the file's content encoded as UTF-8
   * @throws IOException if an I/O error occurs during reading
   * @see #readUTF(Path, long)
   */
  public static StringBuilder readUTF(Path path) throws IOException {
    return readUTF(path, 0);
  }

  /**
   * Reads the content of a file as UTF-8 starting at a specified offset using NIO Path API. The
   * method uses a buffered reader for efficient reading of large files.
   *
   * @param path The path to the file to read
   * @param offset The byte offset in the file at which to start reading
   * @return A StringBuilder containing the file's content from the specified offset, encoded as
   *     UTF-8
   * @throws IOException if an I/O error occurs during reading
   * @throws IllegalArgumentException if offset is negative
   */
  public static StringBuilder readUTF(Path path, long offset) throws IOException {
    StringBuilder result = new StringBuilder();
    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      if (offset > 0) {
        skipFully(reader, offset);
      }
      char[] buffer = new char[4096];
      int length;
      while ((length = reader.read(buffer)) > 0) {
        result.append(buffer, 0, length);
      }
    }
    return result;
  }

  /**
   * Reads the entire content of an input stream as UTF-8. This is a convenience method that
   * delegates to {@link #readUTF(InputStream, long)}.
   *
   * @param stream The input stream to read from
   * @return A StringBuilder containing the stream's content encoded as UTF-8
   * @throws IOException if an I/O error occurs during reading
   * @see #readUTF(InputStream, long)
   */
  public static StringBuilder readUTF(InputStream stream) throws IOException {
    return readUTF(stream, 0);
  }

  /**
   * Reads the content of an input stream as UTF-8, starting at a specified offset. The method uses
   * buffered reading for efficient processing of large streams.
   *
   * @param stream The input stream to read from
   * @param offset The number of bytes to skip before starting to read
   * @return A StringBuilder containing the stream's content from the specified offset, encoded as
   *     UTF-8
   * @throws IOException if an I/O error occurs during reading or skipping
   * @throws IllegalArgumentException if offset is negative
   */
  public static StringBuilder readUTF(InputStream stream, long offset) throws IOException {
    StringBuilder result = new StringBuilder();
    skipFully(stream, offset);
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      char[] buf = new char[4096];
      int length = 0;
      while ((length = reader.read(buf)) > 0) {
        result.append(buf, 0, length);
      }
    }
    return result;
  }

  /**
   * Reliably skips a specified number of bytes from an input stream. This method ensures that the
   * exact number of bytes requested are skipped, unlike {@link InputStream#skip(long)} which may
   * skip fewer bytes.
   *
   * @param is The input stream to skip bytes from
   * @param toSkip The exact number of bytes to skip
   * @throws IllegalArgumentException if toSkip is negative
   * @throws EOFException if the end of stream is reached before the requested number of bytes could
   *     be skipped
   * @throws IOException if an I/O error occurs while skipping
   * @see InputStream#skipNBytes(long)
   */
  public static void skipFully(InputStream is, long toSkip) throws IOException {
    if (toSkip < 0) {
      throw new IllegalArgumentException("Cannot skip negative number of bytes: " + toSkip);
    }

    try {
      is.skipNBytes(toSkip);
    } catch (EOFException e) {
      throw new EOFException("EOF reached while trying to skip " + toSkip + " bytes");
    }
  }

  /**
   * Reliably skips a specified number of characters from a reader. This method ensures that the
   * exact number of characters requested are skipped.
   *
   * @param reader The reader to skip characters from
   * @param toSkip The exact number of characters to skip
   * @throws IllegalArgumentException if toSkip is negative
   * @throws EOFException if the end of reader is reached before the requested number of characters
   *     could be skipped
   * @throws IOException if an I/O error occurs while skipping
   * @see Reader#skip(long)
   */
  public static void skipFully(Reader reader, long toSkip) throws IOException {
    if (toSkip < 0) {
      throw new IllegalArgumentException("Cannot skip negative number of characters: " + toSkip);
    }

    long skipped = reader.skip(toSkip);
    if (skipped != toSkip) {
      throw new EOFException(
          "EOF reached after skipping " + skipped + " characters, expected " + toSkip);
    }
  }

  /**
   * Writes the contents of an input stream to a target file. This is a convenience method that
   * delegates to {@link #writeTo(InputStream, Path)}.
   *
   * @param input The input stream to read data from
   * @param target The target file to write the data to
   * @return {@code true} if the write operation was successful, {@code false} otherwise
   * @throws IOException If an I/O error occurs during the write operation
   * @see #writeTo(InputStream, Path)
   */
  public static boolean writeTo(InputStream input, File target) throws IOException {
    return writeTo(input, target.toPath());
  }

  /**
   * Writes the contents of an input stream to a target path using atomic operations where possible.
   * This method ensures data integrity by:
   *
   * <ul>
   *   <li>Creating a temporary file in the same directory as the target
   *   <li>Writing the input stream contents to the temporary file
   *   <li>Attempting an atomic move from the temporary file to the target
   *   <li>Cleaning up the temporary file regardless of success or failure
   * </ul>
   *
   * <p>The method uses a temporary file to prevent data corruption in case of system crashes or
   * power failures during the write operation.
   *
   * @param input The input stream to read data from
   * @param targetPath The target path where the data should be written
   * @return {@code true} if the write operation was successful, {@code false} otherwise
   * @throws IOException If an I/O error occurs during the write operation, such as:
   *     <ul>
   *       <li>Failure to create the temporary file
   *       <li>Failure to write to the temporary file
   *       <li>Failure to perform the atomic move
   *     </ul>
   *
   * @see #copy(InputStream, OutputStream, long)
   * @see FileSystem#renameTo(Path, Path)
   */
  public static boolean writeTo(InputStream input, Path targetPath) throws IOException {
    // Create temp file in same directory as target for atomic move
    Path tempFile = Files.createTempFile(targetPath.getParent(), "temp", ".tmp");

    try {
      // Copy input stream to temporary file
      try (var output = new FileOutputStream(tempFile.toFile())) {
        copy(input, output, -1);
      }

      // Attempt atomic move from temp to target
      return FileSystem.renameTo(tempFile, targetPath);
    } catch (IOException e) {
      logger.error("Failed to write to {}: {}", targetPath, e.getMessage());
      return false;
    } finally {
      // Clean up temp file if still exists
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Copies bytes from a source input stream to a destination output stream using optimized methods
   * based on the stream types and data length.
   *
   * <p>The method employs several optimization strategies:
   *
   * <ul>
   *   <li>Uses NIO channels for {@link FileInputStream} to {@link FileOutputStream} copies
   *   <li>Falls back to buffered stream copying when channel operations aren't available
   *   <li>Automatically selects appropriate buffer sizes based on the copy length
   * </ul>
   *
   * <p>Performance characteristics:
   *
   * <ul>
   *   <li>For small files (less than 4 buffer sizes): Uses direct buffered copy
   *   <li>For large files: Attempts NIO channel transfer, falls back to buffered copy
   *   <li>For unknown length (-1): Copies until EOF is reached
   * </ul>
   *
   * <p>Error handling:
   *
   * <ul>
   *   <li>Throws EOFException if stream ends before copying specified length
   *   <li>Falls back to buffered copy if channel operations fail
   *   <li>Uses try-with-resources for proper resource management
   * </ul>
   *
   * @param source The input stream to read from
   * @param destination The output stream to write to
   * @param length The number of bytes to copy, or -1 to copy until EOF
   * @throws IOException if an I/O error occurs during the copy operation
   * @throws EOFException if the source stream ends before length bytes are copied
   * @see FileChannel#transferTo(long, long, WritableByteChannel)
   */
  public static void copy(InputStream source, OutputStream destination, long length)
      throws IOException {
    // Try NIO copy first for large files
    if (tryNioCopy(source, destination, length)) {
      return;
    }

    // Fall back to buffered copy
    copyUsingBuffer(source, destination, length);
  }

  /**
   * Compares two input streams for content equality up to a specified size.
   *
   * <p>This method reads and compares the streams byte by byte until either:
   *
   * <ul>
   *   <li>The specified size limit is reached
   *   <li>One or both streams end
   *   <li>A difference is found
   * </ul>
   *
   * <p><strong>Important:</strong> This method will consume data from both input streams up to the
   * specified size. The streams are not reset after reading.
   *
   * <h3>Performance Considerations:</h3>
   *
   * <ul>
   *   <li>Uses buffered reading for efficient comparison of large streams
   *   <li>Stops immediately when a difference is found
   *   <li>Memory efficient - only allocates a small buffer regardless of stream size
   * </ul>
   *
   * <h3>Edge Cases:</h3>
   *
   * <ul>
   *   <li>If both streams are null, returns true
   *   <li>If only one stream is null, returns false
   *   <li>If size is 0 or negative, returns true
   *   <li>If streams have different lengths within the size limit, returns false
   * </ul>
   *
   * @param a the first input stream to compare
   * @param b the second input stream to compare
   * @param size the maximum number of bytes to compare
   * @return true if both streams contain identical content up to the specified size, false
   *     otherwise
   * @throws IOException if an I/O error occurs while reading either stream
   * @see InputStream#read()
   */
  public static boolean equalStreams(@Nullable InputStream a, @Nullable InputStream b, long size)
      throws IOException {
    if (a == b) {
      return true;
    }

    if (a == null || b == null) {
      return false;
    }

    if (size < 0) {
      throw new IllegalArgumentException("Size cannot be negative: " + size);
    }

    try (var bufferedA = new BufferedInputStream(a);
        var bufferedB = new BufferedInputStream(b)) {

      // Use larger buffer for better performance with large files
      byte[] bufferA = new byte[Math.clamp(size / 1024, 8192, BUFFER_SIZE)];
      byte[] bufferB = new byte[bufferA.length];

      long remaining = size;
      while (remaining > 0) {
        int toRead = (int) Math.min(bufferA.length, remaining);

        int readA = bufferedA.readNBytes(bufferA, 0, toRead);
        int readB = bufferedB.readNBytes(bufferB, 0, toRead);

        // Check for premature EOF
        if (readA != readB) {
          return false;
        }

        if (readA == 0) {
          // Both streams reached EOF
          break;
        }

        // Compare chunks using constant-time comparison
        if (!MessageDigest.isEqual(
            Arrays.copyOfRange(bufferA, 0, readA), Arrays.copyOfRange(bufferB, 0, readB))) {
          return false;
        }

        remaining -= readA;
      }

      return true;
    }
  }

  /**
   * Write hard to identify random data to the OutputStream. Does not drain the global secure random
   * number generator, and is significantly faster than it.
   *
   * @param os The stream to write to.
   * @param length The number of bytes to write.
   * @throws IOException If unable to write to the stream.
   */
  public static void fill(OutputStream os, long length) throws IOException {
    long remaining = length;
    byte[] buffer = new byte[BUFFER_SIZE];
    int read = 0;
    while ((remaining == -1) || (remaining > 0)) {
      synchronized (Stream.class) {
        if (cis == null || cisCounter > Long.MAX_VALUE / 2) {
          // Reset it well before the birthday paradox (note this is actually
          // counting bytes).
          byte[] key = new byte[16];
          byte[] iv = new byte[16];
          Global.SECURE_RANDOM.nextBytes(key);
          Global.SECURE_RANDOM.nextBytes(iv);
          var e = AESEngine.newInstance();
          var ctr = SICBlockCipher.newInstance(e);
          ctr.init(true, new ParametersWithIV(new KeyParameter(key), iv));
          cis = new CipherInputStream(zis, new DefaultBufferedBlockCipher(ctr));
          cisCounter = 0;
        }
        read =
            cis.read(
                buffer,
                0,
                ((remaining > BUFFER_SIZE) || (remaining == -1)) ? BUFFER_SIZE : (int) remaining);
        cisCounter += read;
      }
      if (read == -1) {
        if (length == -1) {
          return;
        }
        throw new EOFException("stream reached eof");
      }
      os.write(buffer, 0, read);
      if (remaining > 0) {
        remaining -= read;
      }
    }
  }

  /**
   * Gets a reader for the tail portion of a log file.
   *
   * <p><b>Note:</b> The actual number of bytes read may be slightly more than the specified limit
   * to ensure the first line is complete.
   *
   * @param logfile The log file to read from
   * @param byteLimit Maximum number of bytes to read from the end of the file
   * @return A LineReadingInputStream positioned at the calculated offset
   * @throws IOException If an I/O error occurs while reading the file
   * @throws IllegalArgumentException If the byte limit is negative or the file is invalid
   * @see LineReadingInputStream
   */
  public static LineReadingInputStream getLogTailReader(File logfile, long byteLimit)
      throws IOException {
    return getLogTailReader(logfile.toPath(), byteLimit);
  }

  /**
   * Gets a reader for the tail portion of a log file.
   *
   * <p><b>Note:</b> The actual number of bytes read may be slightly more than the specified limit
   * to ensure the first line is complete.
   *
   * @param logfilePath The path to the log file to read
   * @param byteLimit Maximum number of bytes to read from the end of the file
   * @return A LineReadingInputStream positioned at the calculated offset
   * @throws IOException If an I/O error occurs while reading the file
   * @throws IllegalArgumentException If the byte limit is negative or the file is invalid
   * @see LineReadingInputStream
   */
  public static LineReadingInputStream getLogTailReader(Path logfilePath, long byteLimit)
      throws IOException {
    // Validate inputs
    if (byteLimit < 0) {
      throw new IllegalArgumentException("Byte limit must be non-negative: " + byteLimit);
    }
    if (!Files.exists(logfilePath)) {
      throw new IllegalArgumentException("Path does not exist: " + logfilePath);
    }
    if (!Files.isRegularFile(logfilePath)) {
      throw new IllegalArgumentException("Path is not a regular file: " + logfilePath);
    }
    long fileSize = Files.size(logfilePath);

    // Calculate position to start reading
    long startPosition = Math.max(0, fileSize - byteLimit);

    try (FileInputStream fis = new FileInputStream(logfilePath.toFile());
        LineReadingInputStream lis = new LineReadingInputStream(fis)) {

      // Skip to the calculated position if needed
      if (startPosition > 0) {
        fis.skipNBytes(startPosition);

        // Read and discard the first partial line to ensure we start at a line
        // boundary
        lis.readLine(100000, 200, true);
      }
      return lis;
    }
  }

  /**
   * Attempts to copy streams using NIO channels for better performance. This method only works with
   * {@link FileInputStream} and {@link FileOutputStream}.
   *
   * @param source the input stream to read from
   * @param destination the output stream to write to
   * @param length the number of bytes to copy, or -1 for unknown length
   * @return {@code true} if the copy was successful using NIO, {@code false} if NIO cannot be used
   *     and a fallback is required
   * @see FileChannel#transferTo(long, long, WritableByteChannel)
   */
  private static boolean tryNioCopy(InputStream source, OutputStream destination, long length) {
    if (!(source instanceof FileInputStream && destination instanceof FileOutputStream)) {
      return false;
    }

    try {
      FileChannel sourceChannel = ((FileInputStream) source).getChannel();
      FileChannel destChannel = ((FileOutputStream) destination).getChannel();

      long bytesToTransfer = (length == -1) ? Long.MAX_VALUE : length;
      sourceChannel.transferTo(0, bytesToTransfer, destChannel);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Copies streams using a buffered approach. This method is used as a fallback when NIO channel
   * transfer is not available or fails.
   *
   * <p>The method uses {@link BufferedInputStream} and {@link BufferedOutputStream} for improved
   * performance over direct stream copying.
   *
   * @param source the input stream to read from
   * @param destination the output stream to write to
   * @param length the number of bytes to copy, or -1 for unknown length
   * @throws IOException if an I/O error occurs during copying
   * @throws EOFException if the source stream ends before the specified number of bytes have been
   *     copied
   */
  private static void copyUsingBuffer(InputStream source, OutputStream destination, long length)
      throws IOException {
    try (var bufferedSource = new BufferedInputStream(source);
        var bufferedDest = new BufferedOutputStream(destination)) {

      byte[] buffer = new byte[BUFFER_SIZE];
      long remaining = length;

      while (true) {
        int bytesToRead =
            (remaining == -1 || remaining > BUFFER_SIZE) ? BUFFER_SIZE : (int) remaining;

        int bytesRead = bufferedSource.read(buffer, 0, bytesToRead);
        if (bytesRead == -1) {
          if (length == -1) {
            return;
          }
          throw new EOFException("Stream reached EOF before copying " + length + " bytes");
        }

        bufferedDest.write(buffer, 0, bytesRead);
        if (remaining > 0) {
          remaining -= bytesRead;
          if (remaining == 0) {
            return;
          }
        }
      }
    }
  }
}
