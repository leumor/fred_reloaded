package hyphanet.support.io.stream;

import hyphanet.support.io.DiskSpaceChecker;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * An output stream that checks for available disk space before writing data.
 *
 * <p>This class extends {@link FilterOutputStream} to add disk space checking functionality before
 * actually writing to the underlying output stream. It uses a {@link DiskSpaceChecker} to determine
 * if sufficient disk space is available based on a configurable buffer size. This helps to prevent
 * {@link InsufficientDiskSpaceException} and gracefully handle disk space limitations during write
 * operations.
 */
public class DiskSpaceCheckingOutputStream extends FilterOutputStream {

  /**
   * Creates a new {@link DiskSpaceCheckingOutputStream}.
   *
   * @param out The underlying {@link OutputStream} to which data will be written.
   * @param checker The {@link DiskSpaceChecker} instance used to check disk space.
   * @param path The {@link Path} representing the file to be written to. This path is used by the
   *     {@link DiskSpaceChecker} to determine the disk space.
   * @param bufferSize The buffer size in bytes. Disk space will be checked periodically after this
   *     many bytes have been written since the last check. This is to avoid excessive disk space
   *     checks for every small write operation, improving performance.
   * @throws IllegalArgumentException if {@code bufferSize} is not positive.
   * @throws NullPointerException if {@code out}, {@code checker}, or {@code path} is {@code null}.
   */
  public DiskSpaceCheckingOutputStream(
      OutputStream out, DiskSpaceChecker checker, Path path, int bufferSize) {
    super(out);
    this.checker = checker;
    this.path = path;
    this.bufferSize = bufferSize;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method delegates to {@link #write(byte[], int, int)} with an array of size 1, offset 0,
   * and length 1.
   */
  @Override
  public void write(int i) throws IOException {
    write(new byte[] {(byte) i});
  }

  /**
   * Writes {@code length} bytes from the specified byte array starting at offset {@code offset} to
   * this output stream.
   *
   * <p>This method checks for available disk space before writing to the underlying stream if the
   * number of bytes written since the last check exceeds the configured {@code bufferSize}. The
   * disk space check is performed by the injected {@link DiskSpaceChecker}. If there is
   * insufficient disk space, an {@link InsufficientDiskSpaceException} is thrown.
   *
   * @param buf The byte array to write data from.
   * @param offset The start offset in the byte array.
   * @param length The number of bytes to write.
   * @throws IOException if an I/O error occurs, including {@link InsufficientDiskSpaceException} if
   *     there is not enough disk space.
   * @throws NullPointerException if {@code buf} is {@code null}.
   * @throws IndexOutOfBoundsException if {@code offset} or {@code length} are invalid.
   * @implNote Before writing the provided buffer, this method calculates if a disk space check is
   *     needed. The condition {@code written + length - lastChecked >= bufferSize} determines if
   *     enough bytes have been written since the last disk space check to warrant a new check. If a
   *     check is required, {@link DiskSpaceChecker#checkDiskSpace(Path, int, int)} is called with
   *     the file path, the amount of data to be written ({@code length}), and the {@code
   *     bufferSize}. If the disk space check fails (returns {@code false}), an {@link
   *     InsufficientDiskSpaceException} is thrown, preventing further write operations. If the
   *     check passes or is not required, the data is written to the underlying output stream, and
   *     the {@code written} counter is updated. The {@code lastChecked} counter is updated to the
   *     current {@code written} value after a successful disk space check.
   */
  @Override
  public synchronized void write(byte[] buf, int offset, int length) throws IOException {
    if (written + length - lastChecked >= bufferSize) {
      if (!checker.checkDiskSpace(path, length, bufferSize)) {
        throw new InsufficientDiskSpaceException();
      }
      lastChecked = written;
    }
    out.write(buf, offset, length);
    written += length;
  }

  /**
   * The path to the file being written to.
   *
   * <p>This path is used by the {@link DiskSpaceChecker} to determine the available disk space. It
   * is set during the construction of {@link DiskSpaceCheckingOutputStream} and remains constant
   * throughout the lifetime of the object.
   */
  private final Path path;

  /**
   * The {@link DiskSpaceChecker} instance responsible for checking disk space.
   *
   * <p>This checker is injected during the construction of {@link DiskSpaceCheckingOutputStream}
   * and is used by the {@link #write(byte[], int, int)} method to verify if there is sufficient
   * disk space before writing data.
   */
  private final DiskSpaceChecker checker;

  /**
   * The buffer size in bytes that triggers a disk space check.
   *
   * <p>Disk space is checked periodically after {@link #bufferSize} bytes have been written since
   * the last check. This is to optimize performance by avoiding frequent disk space checks. A
   * larger buffer size reduces the frequency of checks but might delay the detection of
   * insufficient disk space.
   */
  private final int bufferSize;

  /**
   * The total number of bytes written to this stream.
   *
   * <p>This counter is incremented after each successful write operation in the {@link
   * #write(byte[], int, int)} method. It is used in conjunction with {@link #lastChecked} and
   * {@link #bufferSize} to determine when to perform the next disk space check.
   */
  private long written;

  /**
   * The number of bytes written when the disk space was last checked.
   *
   * <p>This value is updated after each successful disk space check in the {@link #write(byte[],
   * int, int)} method. It is used to calculate how many bytes have been written since the last
   * check and to determine if a new disk space check is necessary based on the {@link #bufferSize}.
   */
  private long lastChecked;
}
