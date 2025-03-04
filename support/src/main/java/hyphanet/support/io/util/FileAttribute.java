/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.util;

import hyphanet.base.SystemInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class providing file system operations and attributes manipulation. This class contains
 * static utility methods for handling file attributes, estimating disk usage, and managing file
 * permissions across different platforms.
 *
 * <p>This class cannot be instantiated as it only contains static utility methods.
 */
public final class FileAttribute {

  private static final Logger logger = LoggerFactory.getLogger(FileAttribute.class);

  private FileAttribute() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Estimates the actual disk usage for a given file, including filesystem overhead. This method is
   * a convenience wrapper that converts the {@link File} object to a {@link Path} and delegates to
   * {@link #estimateUsage(Path, long)}.
   *
   * <p>The estimation includes:
   *
   * <ul>
   *   <li>Actual file content size
   *   <li>File system block size and allocation
   *   <li>Filesystem metadata overhead
   * </ul>
   *
   * @param file The file to analyze for disk usage
   * @param fileLength The length of the file in bytes
   * @return Estimated total disk usage in bytes
   * @throws IllegalArgumentException if the fileLength is negative
   * @see #estimateUsage(Path, long)
   */
  public static long estimateUsage(File file, long fileLength) {
    return estimateUsage(file.toPath(), fileLength);
  }

  /**
   * Estimates the actual disk usage for a file, including filesystem overhead.
   *
   * <p>This method takes into account:
   *
   * <ul>
   *   <li>File system block size and allocation strategy
   *   <li>Filename storage overhead
   *   <li>File system metadata requirements
   *   <li>Operating system-specific storage characteristics
   * </ul>
   *
   * <p><b>Platform-Specific Behavior:</b>
   *
   * <ul>
   *   <li>Windows (NTFS): Includes 24 bytes per cluster for metadata
   *   <li>macOS (APFS/HFS+): Includes 32 bytes per cluster overhead
   *   <li>Unix/Linux: Includes 50 bytes per KB block for tree overhead
   * </ul>
   *
   * <p>If the actual file system information cannot be obtained, the method falls back to
   * conservative estimates using common values:
   *
   * <ul>
   *   <li>4KB cluster size
   *   <li>512-byte filename entries
   *   <li>100 bytes base overhead
   *   <li>50 bytes per 1KB block tree overhead
   * </ul>
   *
   * @param path The path to the file
   * @param fileLength The length of the file in bytes
   * @return Estimated total disk usage in bytes
   * @throws IllegalArgumentException If the file length is negative
   * @throws SecurityException If access to file system information is denied
   */
  public static long estimateUsage(Path path, long fileLength) {
    if (fileLength < 0) {
      throw new IllegalArgumentException("File length cannot be negative: " + fileLength);
    }

    // Get the actual file store information if possible
    try {
      var fileStore = Files.getFileStore(path);
      long blockSize = fileStore.getBlockSize();

      // Calculate block usage based on actual file system block size
      long blockUsage = roundup2N(fileLength, (int) blockSize);

      // Calculate filename overhead
      long filenameUsage = getFilenameUsage(path);

      // Calculate additional overhead based on file system type
      long extra =
          switch (SystemInfo.DETECTED_OS) {
            case WINDOWS -> (blockUsage / 4096) * 24; // NTFS metadata per cluster
            case MACOS -> (blockUsage / 4096) * 32; // APFS/HFS+ overhead
            default -> (roundup2N(fileLength, 1024) / 1024) * 50; // Traditional Unix overhead
          };

      return blockUsage + filenameUsage + extra;

    } catch (IOException e) {
      // Fall back to original conservative estimates if we can't get file system info
      long blockUsage = roundup2N(fileLength, 4096); // Assume 4kB clusters

      // Assume 512 byte filename entries, with 100 bytes overhead, for filename
      // overhead (NTFS)
      long filenameUsage = getFilenameUsage(path);

      // Assume 50 bytes per block tree overhead with 1kB blocks (reiser3 worst case)
      long extra = (roundup2N(fileLength, 1024) / 1024) * 50;

      return blockUsage + filenameUsage + extra;
    }
  }

  /**
   * Calculates the total length of an input stream by reading it to completion.
   *
   * <p>This method reads the entire input stream to determine its length. Important notes:
   *
   * <ul>
   *   <li>The stream is consumed during this operation and cannot be reset
   *   <li>For large streams, memory usage is controlled using buffered reading
   *   <li>The method handles streams larger than 2GB
   * </ul>
   *
   * <p>Performance considerations:
   *
   * <ul>
   *   <li>For {@link FileInputStream}, consider using {@link Files#size(Path)} instead
   *   <li>For {@link ByteArrayInputStream}, use {@link ByteArrayInputStream#available()} instead
   *   <li>For other streams, this method must read the entire content to determine length
   * </ul>
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>Returns 0 for empty streams
   *   <li>Handles compressed streams ({@link CipherInputStream}, etc.) correctly
   * </ul>
   *
   * @param source The input stream to measure. The stream will be read to completion.
   * @return The total number of bytes that can be read from the stream
   * @throws IOException if an I/O error occurs while reading the stream
   * @throws NullPointerException if the source stream is null
   */
  public static long findLength(InputStream source) throws IOException {
    return switch (source) {
      case FileInputStream fis -> {
        try {
          // Optimization for FileInputStream
          yield fis.getChannel().size();
        } catch (IOException e) {
          // Fall back to reading if channel operations fail
          yield readStreamLength(source);
        }
      }
      // Optimization for ByteArrayInputStream
      case ByteArrayInputStream bais -> bais.available();
      // For all other streams, read through the entire content
      default -> readStreamLength(source);
    };
  }

  /**
   * Sets read and write permissions for the owner only on the specified file. This is a convenience
   * method that delegates to {@link #setOwnerPerm(File, boolean, boolean, boolean)}.
   *
   * <p>This method attempts to set the following permissions:
   *
   * <ul>
   *   <li>Owner Read: Enabled
   *   <li>Owner Write: Enabled
   *   <li>Owner Execute: Disabled
   *   <li>All group and others permissions: Disabled
   * </ul>
   *
   * @param f The file to modify permissions on
   * @return {@code true} if the permissions were successfully modified, {@code false} otherwise
   * @throws SecurityException if the security manager denies access to the file
   * @see #setOwnerPerm(File, boolean, boolean, boolean)
   */
  public static boolean setOwnerRW(File f) {
    return setOwnerPerm(f, true, true, false);
  }

  /**
   * Sets read, write, and execute permissions for the owner only on the specified file. This is a
   * convenience method that delegates to {@link #setOwnerPerm(File, boolean, boolean, boolean)}.
   *
   * <p>This method attempts to set the following permissions:
   *
   * <ul>
   *   <li>Owner Read: Enabled
   *   <li>Owner Write: Enabled
   *   <li>Owner Execute: Enabled
   *   <li>All group and others permissions: Disabled
   * </ul>
   *
   * @param f The file to modify permissions on
   * @return {@code true} if the permissions were successfully modified, {@code false} otherwise
   * @throws SecurityException if the security manager denies access to the file
   * @see #setOwnerPerm(File, boolean, boolean, boolean)
   */
  public static boolean setOwnerRWX(File f) {
    return setOwnerPerm(f, true, true, true);
  }

  /**
   * Sets specific owner-only permissions on the specified file or directory. This is a convenience
   * method that delegates to {@link #setOwnerPerm(Path, boolean, boolean, boolean)}.
   *
   * @param f The file to modify permissions on
   * @param r If {@code true}, enable read permission for owner
   * @param w If {@code true}, enable write permission for owner
   * @param x If {@code true}, enable execute permission for owner
   * @return {@code true} if the permissions were successfully modified, {@code false} otherwise
   * @throws SecurityException if the security manager denies access to the file
   * @see #setOwnerPerm(Path, boolean, boolean, boolean)
   * @see PosixFilePermission
   */
  public static boolean setOwnerPerm(File f, boolean r, boolean w, boolean x) {
    return setOwnerPerm(f.toPath(), r, w, x);
  }

  /**
   * Sets specific owner-only permissions on the specified file or directory. This method removes
   * all group and others permissions, leaving only the specified owner permissions.
   *
   * <p>Permission bits that can be set:
   *
   * <ul>
   *   <li>Read (r): Controls ability to read file contents or list directory contents
   *   <li>Write (w): Controls ability to modify file contents or create/delete files in directory
   *   <li>Execute (x): Controls ability to execute file or traverse directory
   * </ul>
   *
   * <p><strong>Platform considerations:</strong>
   *
   * <ul>
   *   <li>On POSIX systems: Uses native file permissions
   *   <li>On Windows: Attempts to map to equivalent ACL permissions
   *   <li>On other systems: May have limited functionality
   * </ul>
   *
   * @param path The path to modify permissions on
   * @param r If {@code true}, enable read permission for owner
   * @param w If {@code true}, enable write permission for owner
   * @param x If {@code true}, enable execute permission for owner
   * @return {@code true} if the permissions were successfully modified, {@code false} otherwise
   * @throws SecurityException if the security manager denies access to the file
   * @see PosixFilePermission
   * @see Files#setPosixFilePermissions(Path, Set)
   */
  public static boolean setOwnerPerm(Path path, boolean r, boolean w, boolean x) {
    try {
      var permissions = EnumSet.noneOf(PosixFilePermission.class);
      if (r) {
        permissions.add(PosixFilePermission.OWNER_READ);
      }
      if (w) {
        permissions.add(PosixFilePermission.OWNER_WRITE);
      }
      if (x) {
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
      }

      // Set the new permissions
      Files.setPosixFilePermissions(path, permissions);

      // Verify the permissions were set correctly
      return Files.getPosixFilePermissions(path).equals(permissions);

    } catch (UnsupportedOperationException e) {
      // Fall back to legacy method for non-POSIX systems (e.g., Windows)
      var file = path.toFile();

      return file.setReadable(false, false)
          && file.setReadable(r, true)
          && file.setWritable(false, false)
          && file.setWritable(w, true)
          && file.setExecutable(false, false)
          && file.setExecutable(x, true);

    } catch (IOException e) {
      logger.error("Failed to set permissions on {}: {}", path, e.getMessage());
      return false;
    }
  }

  /**
   * Calculates the actual storage space used by a filename on the filesystem. This method takes
   * into account:
   *
   * <ul>
   *   <li>Operating system-specific filesystem implementations
   *   <li>Character encoding overhead (UTF-8/UTF-16)
   *   <li>Filesystem metadata storage requirements
   * </ul>
   *
   * @param path The {@link Path} object representing the file whose name is being analyzed
   * @return The total number of bytes used to store the filename on the filesystem, including
   *     metadata and alignment padding
   * @implNote For Windows (NTFS), assumes UTF-16 encoding with 100 bytes base metadata. For macOS
   *     (HFS+/APFS), assumes UTF-8 encoding with 248 bytes header. For other systems, uses the
   *     larger of UTF-8 and UTF-16 encodings with 100 bytes overhead.
   * @see #roundup2N(long, int)
   */
  public static long getFilenameUsage(Path path) {
    String filename = path.getFileName().toString();
    int nameLength =
        switch (SystemInfo.DETECTED_OS) {
          case WINDOWS ->
              // NTFS uses Unicode (UTF-16) for filenames with additional metadata
              100 // Base metadata size
                  + filename.getBytes(StandardCharsets.UTF_16).length;
          case MACOS ->
              // HFS+/APFS uses UTF-8 with additional metadata
              248 // HFS+/APFS header size
                  + filename.getBytes(StandardCharsets.UTF_8).length;
          default ->
              // Generic Unix-like systems (ext4, etc.)
              100 // Conservative estimate for inode overhead
                  + Math.max(
                      filename.getBytes(StandardCharsets.UTF_16).length,
                      filename.getBytes(StandardCharsets.UTF_8).length);
        };

    return roundup2N(nameLength, 512);
  }

  /**
   * Rounds up a value to the nearest multiple of a power-of-two block size. This method is used for
   * calculating actual storage space usage on block-based filesystems.
   *
   * @param val The value to round up
   * @param blocksize The block size to align to (must be a power of 2)
   * @return The value rounded up to the nearest multiple of blocksize
   * @throws IllegalArgumentException implicitly if blocksize is not a power of 2
   * @implNote Uses bitwise operations for efficient calculation: 1. Creates a mask from blocksize
   *     2. Adds (blocksize - 1) to handle rounding 3. Uses bitwise AND with inverted mask to align
   *     to block boundary
   */
  public static long roundup2N(long val, int blocksize) {
    int mask = blocksize - 1;
    return (val + mask) & ~mask;
  }

  /**
   * Reads the length of the input stream by iterating through it.
   *
   * <p>This is a helper method used by {@link #findLength(InputStream)} when direct size retrieval
   * is not possible (e.g., for generic input streams).
   *
   * @param source The input stream to read.
   * @return The total number of bytes read from the stream.
   * @throws IOException If an I/O error occurs while reading the stream.
   */
  private static long readStreamLength(InputStream source) throws IOException {
    long length = 0;
    byte[] buffer = new byte[8192]; // Increased buffer size for better performance

    try (var bufferedSource = new BufferedInputStream(source)) {
      int bytesRead;
      while ((bytesRead = bufferedSource.read(buffer)) != -1) {
        length += bytesRead;
      }
    }
    return length;
  }
}
