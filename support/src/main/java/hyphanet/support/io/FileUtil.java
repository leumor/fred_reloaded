/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import hyphanet.crypt.Global;
import hyphanet.support.StringValidityChecker;
import hyphanet.support.io.bucket.BucketTools;
import hyphanet.support.io.bucket.RegularFile;
import hyphanet.support.io.stream.LineReadingInputStream;
import hyphanet.support.io.stream.ZeroInputStream;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.engines.AESFastEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class providing file system operations and helper methods for file manipulation.
 * This class handles common file operations with proper error handling and platform-specific
 * considerations.
 *
 * <p>Key features include:
 * <ul>
 *   <li>File reading and writing with proper character encoding</li>
 *   <li>Cross-platform path manipulation and validation</li>
 *   <li>File system operations like copy, move, and delete</li>
 *   <li>Permission management and security controls</li>
 * </ul>
 */
final public class FileUtil {
    /**
     * Default buffer size used for file operations. Set to 32KB for optimal performance on
     * most file systems.
     */
    public static final int BUFFER_SIZE = 32 * 1024;
    /**
     * The detected operating system for the current environment. This is determined at class
     * initialization time.
     */
    public static final OperatingSystem DETECTED_OS = detectOperatingSystem();
    /**
     * The detected CPU architecture for the current environment.
     *
     * <p><b>Note:</b> This detection may not always be 100% accurate in cases where:
     * <ul>
     *   <li>32-bit vs 64-bit detection is ambiguous</li>
     *   <li>Wrong JVM architecture is used for the platform</li>
     *   <li>System uses architecture emulation or compatibility layers</li>
     * </ul>
     */
    public static final CPUArchitecture DETECTED_ARCH = detectCPUArchitecture();

    /**
     * Enumeration of supported operating systems with platform-specific capabilities.
     *
     * <p>Each enum constant provides information about:
     * <ul>
     *   <li>Windows compatibility</li>
     *   <li>macOS compatibility</li>
     *   <li>Unix/Linux compatibility</li>
     * </ul>
     */
    public enum OperatingSystem {
        UNKNOWN(false, false, false),
        MACOS(false, true, true),
        LINUX(false, false, true),
        FREEBSD(false, false, true),
        GENERIC_UNIX(false, false, true),
        WINDOWS(true, false, false);

        OperatingSystem(boolean win, boolean mac, boolean unix) {
            this.isWindows = win;
            this.isMac = mac;
            this.isUnix = unix;
        }

        public boolean isWindows() {
            return isWindows;
        }

        public boolean isMac() {
            return isMac;
        }

        public boolean isUnix() {
            return isUnix;
        }

        private final boolean isWindows;
        private final boolean isMac;
        private final boolean isUnix;
    }

    /**
     * CPU architecture using modern naming conventions
     */
    public enum CPUArchitecture {
        UNKNOWN, X86_32, X86_64, ARM_32, ARM_64, RISCV_64, PPC_32, PPC_64, IA64
    }

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    // I did not find any way to detect the Charset of the file system so I'm using the file
    // encoding charset.
    // On Windows and Linux this is set based on the users configured system language which is
    // probably equal to the filename charset.
    // The worst thing which can happen if we misdetect the filename charset is that downloads
    // fail because the filenames are invalid:
    // We disallow path and file separator characters anyway so its not possible to cause
    // files to
    // be stored in arbitrary places.
    private static final Charset FILE_NAME_CHARSET = getFileEncodingCharset();
    private static final ZeroInputStream zis = new ZeroInputStream();
    private static volatile boolean logMINOR;
    private static CipherInputStream cis;
    private static long cisCounter;

    /**
     * Gets a reader for the tail portion of a log file.
     *
     * <p><b>Note:</b> The actual number of bytes read may be slightly more than the specified
     * limit to ensure the first line is complete.
     *
     * @param logfile   The log file to read from
     * @param byteLimit Maximum number of bytes to read from the end of the file
     *
     * @return A LineReadingInputStream positioned at the calculated offset
     *
     * @throws IOException              If an I/O error occurs while reading the file
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
     * <p><b>Note:</b> The actual number of bytes read may be slightly more than the specified
     * limit to ensure the first line is complete.
     *
     * @param logfilePath The path to the log file to read
     * @param byteLimit   Maximum number of bytes to read from the end of the file
     *
     * @return A LineReadingInputStream positioned at the calculated offset
     *
     * @throws IOException              If an I/O error occurs while reading the file
     * @throws IllegalArgumentException If the byte limit is negative or the file is invalid
     * @see LineReadingInputStream
     */
    public static LineReadingInputStream getLogTailReader(Path logfilePath, long byteLimit)
        throws IOException {
        // Validate inputs
        if (byteLimit < 0) {
            throw new IllegalArgumentException(
                "Byte limit must be non-negative: " + byteLimit);
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
     * Returns the Charset corresponding to the system's "file.encoding" property.
     *
     * <p>This method provides access to the system's default file encoding, which is
     * typically:
     * <ul>
     *   <li>On Windows: Set to the user's configured system language</li>
     *   <li>On Unix/Linux: Usually UTF-8</li>
     *   <li>On macOS: Usually UTF-8</li>
     * </ul>
     * <p>
     * The method first attempts to use the system's default charset. If this fails,
     * it falls back to UTF-8 to ensure a valid Charset is always returned.
     *
     * @return The system's default file encoding Charset, or UTF-8 if the default cannot be
     * determined
     *
     * @see Charset#defaultCharset()
     * @see StandardCharsets#UTF_8
     */
    public static Charset getFileEncodingCharset() {
        try {
            return Charset.forName(Charset.defaultCharset().displayName());
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * Estimates the actual disk usage for a file, including filesystem overhead. This is a
     * convenience method that delegates to {@link #estimateUsage(Path, long)}.
     *
     * @param file The file to analyze
     * @param flen The length of the file in bytes
     *
     * @return Estimated total disk usage in bytes
     *
     * @throws IllegalArgumentException If the file length is negative
     * @see #estimateUsage(Path, long)
     */
    public static long estimateUsage(File file, long flen) {
        return estimateUsage(file.toPath(), flen);
    }

    /**
     * Estimates the actual disk usage for a file, including filesystem overhead.
     *
     * <p>This method takes into account:
     * <ul>
     *   <li>File system block size and allocation strategy</li>
     *   <li>Filename storage overhead</li>
     *   <li>File system metadata requirements</li>
     *   <li>Operating system-specific storage characteristics</li>
     * </ul>
     *
     * <p><b>Platform-Specific Behavior:</b>
     * <ul>
     *   <li>Windows (NTFS): Includes 24 bytes per cluster for metadata</li>
     *   <li>macOS (APFS/HFS+): Includes 32 bytes per cluster overhead</li>
     *   <li>Unix/Linux: Includes 50 bytes per KB block for tree overhead</li>
     * </ul>
     * <p>
     * If the actual file system information cannot be obtained, the method falls back
     * to conservative estimates using common values:
     * <ul>
     *   <li>4KB cluster size</li>
     *   <li>512-byte filename entries</li>
     *   <li>100 bytes base overhead</li>
     *   <li>50 bytes per 1KB block tree overhead</li>
     * </ul>
     *
     * @param path The path to the file
     * @param flen The length of the file in bytes
     *
     * @return Estimated total disk usage in bytes
     *
     * @throws IllegalArgumentException If the file length is negative
     * @throws SecurityException        If access to file system information is denied
     */
    public static long estimateUsage(Path path, long flen) {
        if (flen < 0) {
            throw new IllegalArgumentException("File length cannot be negative");
        }

        // Get the actual file store information if possible
        try {
            var fileStore = Files.getFileStore(path);
            long blockSize = fileStore.getBlockSize();

            // Calculate block usage based on actual file system block size
            long blockUsage = roundup_2n(flen, (int) blockSize);

            // Calculate filename overhead
            long filenameUsage = getFilenameUsage(path);

            // Calculate additional overhead based on file system type
            long extra = switch (DETECTED_OS) {
                case WINDOWS -> (blockUsage / 4096) * 24;  // NTFS metadata per cluster
                case MACOS -> (blockUsage / 4096) * 32;    // APFS/HFS+ overhead
                default -> (roundup_2n(flen, 1024) / 1024) * 50; // Traditional Unix overhead
            };

            return blockUsage + filenameUsage + extra;

        } catch (IOException e) {
            // Fall back to original conservative estimates if we can't get file system info
            long blockUsage = roundup_2n(flen, 4096);  // Assume 4kB clusters
            // Assume 512 byte filename entries, with 100 bytes overhead, for filename
            // overhead (NTFS)
            String filename = path.getFileName().toString();
            int nameLength = 100 + Math.max(
                filename.getBytes(StandardCharsets.UTF_16).length,
                filename.getBytes(StandardCharsets.UTF_8).length
            );
            long filenameUsage = roundup_2n(nameLength, 512);
            // Assume 50 bytes per block tree overhead with 1kB blocks (reiser3 worst case)
            long extra = (roundup_2n(flen, 1024) / 1024) * 50;

            return blockUsage + filenameUsage + extra;
        }
    }

    /**
     * Determines if one file is a parent directory of another file. This is a convenience
     * method that delegates to {@link #isParent(Path, Path)}.
     *
     * @param poss     The potential parent file
     * @param filename The file to check
     *
     * @return {@code true} if poss is a parent of filename or if they are the same path,
     * {@code false} if they are unrelated or if an error occurs
     *
     * @see #isParent(Path, Path)
     */
    public static boolean isParent(File poss, File filename) {
        return isParent(poss.toPath(), filename.toPath());
    }

    /**
     * Determines if one path is a parent directory of another path.
     *
     * <p>This method performs the following checks:
     * <ul>
     *   <li>Resolves symbolic links in both paths</li>
     *   <li>Normalizes paths to handle different path separators</li>
     *   <li>Checks if paths are identical (considered as parent)</li>
     *   <li>Verifies if one path is a parent directory of another</li>
     * </ul>
     * <p>
     * This method safely handles symbolic links by resolving them before comparison,
     * preventing path traversal attacks.
     * <p>
     * If any I/O errors occur during path resolution (e.g., file not found,
     * insufficient permissions), the method returns {@code false} rather than
     * throwing an exception.
     *
     * @param poss     The potential parent path to check
     * @param filename The path that might be a child of the parent
     *
     * @return {@code true} if poss is a parent of filename or if they are the same path,
     * {@code false} if they are unrelated or if an error occurs
     */
    public static boolean isParent(Path poss, Path filename) {
        try {
            // Convert to Path objects and normalize
            Path possPath = poss.toRealPath();
            Path filePath = filename.toRealPath();

            // Check if paths are equal
            if (possPath.equals(filePath)) {
                return true;
            }

            // Check if one path is parent of another
            return filePath.startsWith(possPath);

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Returns the canonical form of the specified file. This is a convenience method that
     * delegates to {@link #getCanonicalFile(Path)}.
     *
     * @param file The file to canonicalize
     *
     * @return The canonical file, or the absolute file if canonicalization fails
     *
     * @throws SecurityException if the security manager denies access to the file
     * @see #getCanonicalFile(Path)
     */
    public static File getCanonicalFile(File file) {
        try {
            return getCanonicalFile(file.toPath()).toFile();
        } catch (SecurityException e) {
            return file.getAbsoluteFile();
        }
    }

    /**
     * Returns the canonical form of the specified path, resolving symbolic links and
     * normalizing the path name.
     *
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Normalizes path based on operating system conventions</li>
     *   <li>Resolves all symbolic links in the path</li>
     *   <li>Removes redundant name elements like "." and ".."</li>
     *   <li>Standardizes path separators for the platform</li>
     * </ul>
     *
     * <p><b>Platform-Specific Behavior:</b>
     * <ul>
     *   <li>Windows: Converts path to lowercase for case-insensitive comparison</li>
     *   <li>Unix/Linux: Preserves case sensitivity of the original path</li>
     *   <li>All platforms: Uses platform-specific path separators</li>
     * </ul>
     * <p>
     * If any errors occur during canonicalization (such as broken symbolic links
     * or insufficient permissions), the method falls back to returning the absolute
     * path in normalized form.
     *
     * @param path The path to canonicalize
     *
     * @return The canonical path, or the absolute normalized path if canonicalization fails
     *
     * @throws SecurityException if the security manager denies access to the file system
     */
    public static Path getCanonicalFile(Path path) {
        try {
            // Normalize the path string based on OS
            String normalizedPath =
                (DETECTED_OS == OperatingSystem.WINDOWS) ? path.toString().toLowerCase() :
                    path.toString();

            // Create new path from normalized string
            path = Path.of(normalizedPath);

            // Resolve symbolic links and normalize path
            return path.toRealPath();

        } catch (IOException e) {
            // If we can't get the real path, fall back to absolute path
            return path.toAbsolutePath().normalize();
        } catch (SecurityException e) {
            // If security manager prevents access, return absolute file
            return path.toAbsolutePath().normalize();
        }
    }

    /**
     * Reads the entire content of a file as UTF-8 and returns it as a StringBuilder. This is a
     * convenience method that delegates to {@link #readUTF(Path)}.
     *
     * @param file The file to read the content from
     *
     * @return A StringBuilder containing the file's content encoded as UTF-8
     *
     * @throws FileNotFoundException if the file does not exist or cannot be opened for
     *                               reading
     * @throws IOException           if an I/O error occurs during reading
     * @see #readUTF(Path)
     */
    public static StringBuilder readUTF(File file) throws IOException {
        return readUTF(file, 0);
    }

    /**
     * Reads the content of a file as UTF-8, starting at a specified offset. This is a
     * convenience method that delegates to {@link #readUTF(Path, long)}.
     *
     * @param file   The file to read the content from
     * @param offset The byte offset in the file at which to start reading
     *
     * @return A StringBuilder containing the file's content from the specified offset, encoded
     * as UTF-8
     *
     * @throws FileNotFoundException    if the file does not exist or cannot be opened for
     *                                  reading
     * @throws IOException              if an I/O error occurs during reading
     * @throws IllegalArgumentException if offset is negative
     * @see #readUTF(Path, long)
     */
    public static StringBuilder readUTF(File file, long offset) throws IOException {
        return readUTF(file.toPath(), offset);
    }

    /**
     * Reads the entire content of a file as UTF-8 using NIO Path API. This is a convenience
     * method that delegates to {@link #readUTF(Path, long)}.
     *
     * @param path The path to the file to read
     *
     * @return A StringBuilder containing the file's content encoded as UTF-8
     *
     * @throws IOException if an I/O error occurs during reading
     * @see #readUTF(Path, long)
     */
    public static StringBuilder readUTF(Path path) throws IOException {
        return readUTF(path, 0);
    }

    /**
     * Reads the content of a file as UTF-8 starting at a specified offset using NIO Path API.
     * The method uses a buffered reader for efficient reading of large files.
     *
     * @param path   The path to the file to read
     * @param offset The byte offset in the file at which to start reading
     *
     * @return A StringBuilder containing the file's content from the specified offset, encoded
     * as UTF-8
     *
     * @throws IOException              if an I/O error occurs during reading
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
     *
     * @return A StringBuilder containing the stream's content encoded as UTF-8
     *
     * @throws IOException if an I/O error occurs during reading
     * @see #readUTF(InputStream, long)
     */
    public static StringBuilder readUTF(InputStream stream) throws IOException {
        return readUTF(stream, 0);
    }

    /**
     * Reads the content of an input stream as UTF-8, starting at a specified offset. The
     * method uses buffered reading for efficient processing of large streams.
     *
     * @param stream The input stream to read from
     * @param offset The number of bytes to skip before starting to read
     *
     * @return A StringBuilder containing the stream's content from the specified offset,
     * encoded as UTF-8
     *
     * @throws IOException              if an I/O error occurs during reading or skipping
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
     * Reliably skips a specified number of bytes from an input stream. This method ensures
     * that the exact number of bytes requested are skipped, unlike
     * {@link InputStream#skip(long)} which may skip fewer bytes.
     *
     * @param is     The input stream to skip bytes from
     * @param toSkip The exact number of bytes to skip
     *
     * @throws IllegalArgumentException if toSkip is negative
     * @throws EOFException             if the end of stream is reached before the requested
     *                                  number of bytes could be skipped
     * @throws IOException              if an I/O error occurs while skipping
     * @see InputStream#skipNBytes(long)
     */
    public static void skipFully(InputStream is, long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException(
                "Cannot skip negative number of bytes: " + toSkip);
        }

        try {
            is.skipNBytes(toSkip);
        } catch (EOFException e) {
            throw new EOFException("EOF reached while trying to skip " + toSkip + " bytes");
        }
    }

    /**
     * Reliably skips a specified number of characters from a reader. This method ensures that
     * the exact number of characters requested are skipped.
     *
     * @param reader The reader to skip characters from
     * @param toSkip The exact number of characters to skip
     *
     * @throws IllegalArgumentException if toSkip is negative
     * @throws EOFException             if the end of reader is reached before the requested
     *                                  number of characters could be skipped
     * @throws IOException              if an I/O error occurs while skipping
     * @see Reader#skip(long)
     */
    public static void skipFully(Reader reader, long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException(
                "Cannot skip negative number of characters: " + toSkip);
        }

        long skipped = reader.skip(toSkip);
        if (skipped != toSkip) {
            throw new EOFException(
                "EOF reached after skipping " + skipped + " characters, expected " + toSkip);
        }
    }

    /**
     * Writes the contents of an input stream to a target file. This is a convenience method
     * that delegates to {@link #writeTo(InputStream, Path)}.
     *
     * @param input  The input stream to read data from
     * @param target The target file to write the data to
     *
     * @return {@code true} if the write operation was successful, {@code false} otherwise
     *
     * @throws IOException If an I/O error occurs during the write operation
     * @see #writeTo(InputStream, Path)
     */
    public static boolean writeTo(InputStream input, File target) throws IOException {
        return writeTo(input, target.toPath());
    }

    /**
     * Writes the contents of an input stream to a target path using atomic operations where
     * possible. This method ensures data integrity by:
     * <ul>
     *   <li>Creating a temporary file in the same directory as the target</li>
     *   <li>Writing the input stream contents to the temporary file</li>
     *   <li>Attempting an atomic move from the temporary file to the target</li>
     *   <li>Cleaning up the temporary file regardless of success or failure</li>
     * </ul>
     *
     * <p>The method uses a temporary file to prevent data corruption in case of system crashes
     * or power failures during the write operation.</p>
     *
     * @param input      The input stream to read data from
     * @param targetPath The target path where the data should be written
     *
     * @return {@code true} if the write operation was successful, {@code false} otherwise
     *
     * @throws IOException If an I/O error occurs during the write operation, such as:
     *                     <ul>
     *                       <li>Failure to create the temporary file</li>
     *                       <li>Failure to write to the temporary file</li>
     *                       <li>Failure to perform the atomic move</li>
     *                     </ul>
     * @see #copy(InputStream, OutputStream, long)
     * @see #renameTo(Path, Path)
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
            return renameTo(tempFile, targetPath);
        } catch (IOException e) {
            logger.error("Failed to write to {}: {}", targetPath, e.getMessage());
            return false;
        } finally {
            // Clean up temp file if still exists
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Renames a file with atomic move support. This method attempts to perform an atomic move
     * operation first, falling back to a regular move if atomic operations are not supported
     * by the filesystem.
     *
     * @param source The source Path to move from
     * @param target The target Path to move to
     *
     * @return {@code true} if the rename was successful, {@code false} if the operation failed
     *
     * @throws IllegalArgumentException if the source and target paths are the same or if the
     *                                  source path does not exist
     */
    public static boolean renameTo(Path source, Path target) {
        if (source.equals(target)) {
            throw new IllegalArgumentException("Source and target paths are the same");
        }

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Source path does not exist: " + source);
        }

        try {
            // Attempt atomic move first
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            // If atomic move is not supported, try regular move with replace
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException moveError) {
                logger.error(
                    "Failed to rename {} to {}{}{}",
                    source,
                    target,
                    Files.exists(target) ? " (target exists)" : "",
                    Files.exists(source) ? " (source exists)" : "",
                    moveError
                );
                return false;
            }
        } catch (IOException e) {
            logger.error(
                "Failed to rename {} to {}{}{}",
                source,
                target,
                Files.exists(target) ? " (target exists)" : "",
                Files.exists(source) ? " (source exists)" : "",
                e
            );
            return false;
        }
    }

    /**
     * Renames a file with atomic move support. This is a convenience method that delegates to
     * {@link #renameTo(Path, Path)}.
     *
     * @param orig The source File to move from
     * @param dest The target File to move to
     *
     * @return {@code true} if the rename was successful, {@code false} if the operation failed
     *
     * @throws IllegalArgumentException if the source and destination files are the same or if
     *                                  the source file does not exist
     * @see #renameTo(Path, Path)
     */
    public static boolean renameTo(File orig, File dest) {
        if (orig.equals(dest)) {
            throw new IllegalArgumentException("Source and destination files are the same");
        }

        if (!orig.exists()) {
            throw new IllegalArgumentException("Source file does not exist: " + orig);
        }

        return renameTo(orig.toPath(), dest.toPath());
    }

    /**
     * Like renameTo(), but can move across filesystems, by copying the data.
     *
     * @param orig
     * @param dest
     * @param overwrite
     */
    public static boolean moveTo(Path orig, Path dest, boolean overwrite) {
        if (orig.equals(dest)) {
            throw new IllegalArgumentException("Huh? the two file descriptors are the same!");
        }
        if (!Files.exists(orig)) {
            throw new IllegalArgumentException("Original doesn't exist!");
        }
        if (Files.exists(dest)) {
            if (overwrite) {
                try {
                    Files.delete(dest);
                } catch (IOException e) {
                    logger.error(
                        "Not overwriting {} - already exists moving {} and unable to delete " +
                        "it", dest, orig, e
                    );
                    return false;
                }
            } else {
                logger.error("Not overwriting {} - already exists moving {}", dest, orig);
                return false;
            }
        }
        try {
            Files.move(orig, dest);
        } catch (IOException e) {
            logger.warn("Unable to move {} to {}. Copying instead.", orig, dest, e);
            return copyFile(orig, dest);
        }

        return true;
    }

    /**
     * Sanitizes a filename to ensure it is valid for the specified operating system. The
     * method removes or replaces invalid characters and handles platform-specific filename
     * restrictions.
     *
     * <p>The sanitization process includes:</p>
     * <ul>
     *   <li>Filtering out invalid charset characters</li>
     *   <li>Replacing OS-specific reserved characters</li>
     *   <li>Handling platform-specific filename restrictions</li>
     *   <li>Ensuring the result is a valid filename across different filesystems</li>
     * </ul>
     *
     * <p>For {@code OperatingSystem.UNKNOWN}, the method applies the most restrictive
     * combination
     * of rules to ensure compatibility across all supported platforms:</p>
     * <ul>
     *   <li>Windows: {@code <>:"/\|?*}</li>
     *   <li>macOS: {@code /:}</li>
     *   <li>Unix/Linux: {@code /}</li>
     * </ul>
     *
     * <p>Special cases handled:</p>
     * <ul>
     *   <li>Empty filenames are replaced with "Invalid_filename"</li>
     *   <li>Windows reserved names (e.g., CON, PRN) are prefixed with an underscore</li>
     *   <li>Trailing dots and spaces are removed for Windows compatibility</li>
     * </ul>
     *
     * @param fileName   The original filename to sanitize
     * @param targetOS   The target operating system to sanitize for
     * @param extraChars Additional characters to be considered valid (not replaced)
     *
     * @return A sanitized filename that is valid for the target operating system
     *
     * @throws IllegalArgumentException if fileName is null
     * @see OperatingSystem
     */
    public static String sanitizeFileName(
        String fileName, OperatingSystem targetOS, String extraChars) {
        if (fileName.isEmpty()) {
            return "Invalid_filename";
        }

        // Filter out invalid charset characters
        CharBuffer buffer = FILE_NAME_CHARSET.decode(FILE_NAME_CHARSET.encode(fileName));

        // Determine replacement character
        char replacementChar = determineReplacementChar(extraChars);

        // Create string builder with estimated capacity
        StringBuilder sb = new StringBuilder(fileName.length());

        // Get reserved characters based on OS
        String reservedChars = switch (targetOS) {
            case WINDOWS -> "<>:\"/\\|?*";
            case MACOS -> "/:";
            case LINUX, FREEBSD, GENERIC_UNIX -> "/";
            case UNKNOWN -> "<>:\"/\\|?*/:"; // Most restrictive combination
        };

        // Process each character
        buffer.chars().forEach(c -> {
            if (shouldReplace(c, targetOS, extraChars, reservedChars)) {
                sb.append(replacementChar);
            } else {
                sb.append((char) c);
            }
        });

        // Handle Windows-specific restrictions
        String result = sb.toString().trim();
        if (targetOS == OperatingSystem.UNKNOWN || targetOS == OperatingSystem.WINDOWS) {
            // Remove trailing dots and spaces
            result = result.replaceAll("[. ]+$", "");

            // Handle Windows reserved names
            if (isWindowsReservedName(result)) {
                result = "_" + result;
            }
        }

        return result.isEmpty() ? "Invalid_filename" : result;
    }

    /**
     * Sanitizes a filename using the detected operating system's rules. This is a convenience
     * method that delegates to {@link #sanitizeFileName(String, OperatingSystem, String)} with
     * no extra allowed characters.
     *
     * <p>The method uses the system's detected operating system ({@link #DETECTED_OS}) to
     * determine which character restrictions to apply. This ensures the filename will be valid
     * on the current platform.</p>
     *
     * @param fileName The filename to sanitize
     *
     * @return A sanitized filename that is valid for the current operating system
     *
     * @throws IllegalArgumentException if fileName is null
     * @see #sanitizeFileName(String, OperatingSystem, String)
     * @see #DETECTED_OS
     */
    public static String sanitize(String fileName) {
        return sanitizeFileName(fileName, DETECTED_OS, "");
    }

    /**
     * Sanitizes a filename using the detected operating system's rules, allowing additional
     * specified characters to be considered valid. This is a convenience method that delegates
     * to {@link #sanitizeFileName(String, OperatingSystem, String)}.
     *
     * <p>This method extends the basic sanitization by allowing specific characters to be
     * preserved in the filename. This is useful when certain special characters are known to
     * be safe in a particular context.</p>
     *
     * <p>Example usage:</p>
     * <pre>
     * // Allow hyphens and plus signs in filenames
     * String safe = sanitizeFileNameWithExtras("my-file+name.txt", "-+");
     * </pre>
     *
     * @param fileName   The filename to sanitize
     * @param extraChars Additional characters to be considered valid (not replaced)
     *
     * @return A sanitized filename that is valid for the current operating system, preserving
     * the specified extra characters
     *
     * @throws IllegalArgumentException if fileName is null
     * @see #sanitizeFileName(String, OperatingSystem, String)
     * @see #DETECTED_OS
     */
    public static String sanitizeFileNameWithExtras(String fileName, String extraChars) {
        return sanitizeFileName(fileName, DETECTED_OS, extraChars);
    }

    // TODO
    //    public static String sanitize(String filename, String mimeType) {
    //        filename = sanitize(filename);
    //        if (mimeType == null) {
    //            return filename;
    //        }
    //        return DefaultMIMETypes.forceExtension(filename, mimeType);
    //    }

    /**
     * Calculates the total length of an input stream by reading it to completion.
     *
     * <p>This method reads the entire input stream to determine its length. Important
     * notes:</p>
     * <ul>
     *   <li>The stream is consumed during this operation and cannot be reset</li>
     *   <li>For large streams, memory usage is controlled using buffered reading</li>
     *   <li>The method handles streams larger than 2GB</li>
     * </ul>
     *
     * <p>Performance considerations:</p>
     * <ul>
     *   <li>For {@link FileInputStream}, consider using {@link Files#size(Path)} instead</li>
     *   <li>For {@link ByteArrayInputStream}, use {@link ByteArrayInputStream#available()}
     *   instead</li>
     *   <li>For other streams, this method must read the entire content to determine
     *   length</li>
     * </ul>
     *
     * <p>Special cases:</p>
     * <ul>
     *   <li>Returns 0 for empty streams</li>
     *   <li>Handles compressed streams ({@link CipherInputStream}, etc.) correctly</li>
     * </ul>
     *
     * @param source The input stream to measure. The stream will be read to completion.
     *
     * @return The total number of bytes that can be read from the stream
     *
     * @throws IOException          if an I/O error occurs while reading the stream
     * @throws NullPointerException if the source stream is null
     */
    public static long findLength(InputStream source) throws IOException {
        // Optimization for FileInputStream
        if (source instanceof FileInputStream fis) {
            try {
                return fis.getChannel().size();
            } catch (IOException e) {
                // Fall back to reading if channel operations fail
            }
        }

        // Optimization for ByteArrayInputStream
        if (source instanceof ByteArrayInputStream bais) {
            return bais.available();
        }

        // For all other streams, read through the entire content
        long length = 0;
        byte[] buffer = new byte[8192]; // Increased buffer size for better performance

        // Use try-with-resources with BufferedInputStream for better performance
        try (var bufferedSource = new BufferedInputStream(source)) {
            int bytesRead;
            while ((bytesRead = bufferedSource.read(buffer)) != -1) {
                length += bytesRead;
            }
        }

        return length;
    }

    /**
     * Copies bytes from a source input stream to a destination output stream using optimized
     * methods based on the stream types and data length.
     *
     * <p>The method employs several optimization strategies:</p>
     * <ul>
     *   <li>Uses NIO channels for {@link FileInputStream} to {@link FileOutputStream}
     *   copies</li>
     *   <li>Falls back to buffered stream copying when channel operations aren't
     *   available</li>
     *   <li>Automatically selects appropriate buffer sizes based on the copy length</li>
     * </ul>
     *
     * <p>Performance characteristics:</p>
     * <ul>
     *   <li>For small files (less than 4 buffer sizes): Uses direct buffered copy</li>
     *   <li>For large files: Attempts NIO channel transfer, falls back to buffered copy</li>
     *   <li>For unknown length (-1): Copies until EOF is reached</li>
     * </ul>
     *
     * <p>Error handling:</p>
     * <ul>
     *   <li>Throws EOFException if stream ends before copying specified length</li>
     *   <li>Falls back to buffered copy if channel operations fail</li>
     *   <li>Uses try-with-resources for proper resource management</li>
     * </ul>
     *
     * @param source      The input stream to read from
     * @param destination The output stream to write to
     * @param length      The number of bytes to copy, or -1 to copy until EOF
     *
     * @throws IOException  if an I/O error occurs during the copy operation
     * @throws EOFException if the source stream ends before length bytes are copied
     * @see FileChannel#transferTo(long, long, WritableByteChannel)
     */
    public static void copy(InputStream source, OutputStream destination, long length)
        throws IOException {
        // For unknown lengths or large files, use buffered copy
        if (length == -1 || length > BUFFER_SIZE * 4) {
            try {
                // Try to use NIO copy if both streams support channels
                if (source instanceof FileInputStream &&
                    destination instanceof FileOutputStream) {
                    FileChannel sourceChannel = ((FileInputStream) source).getChannel();
                    FileChannel destChannel = ((FileOutputStream) destination).getChannel();

                    if (length == -1) {
                        sourceChannel.transferTo(0, Long.MAX_VALUE, destChannel);
                    } else {
                        sourceChannel.transferTo(0, length, destChannel);
                    }
                    return;
                }
            } catch (IOException e) {
                // Fall back to stream copy if channel operations fail
            }
        }

        // Fall back to buffered stream copy for small files or when channels aren't available
        try (var bufferedSource = new BufferedInputStream(source);
             var bufferedDest = new BufferedOutputStream(destination)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = length;

            while ((remaining == -1) || (remaining > 0)) {
                int bytesToRead = (remaining == -1 || remaining > BUFFER_SIZE) ? BUFFER_SIZE :
                    (int) remaining;

                int bytesRead = bufferedSource.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) {
                    if (length == -1) {
                        return;
                    }
                    throw new EOFException(
                        "Stream reached EOF before copying " + length + " bytes");
                }

                bufferedDest.write(buffer, 0, bytesRead);
                if (remaining > 0) {
                    remaining -= bytesRead;
                }
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents. This method should be used with
     * extreme caution as it permanently deletes all files and subdirectories.
     *
     * <p><strong>Warning:</strong> This is a destructive operation that cannot be undone.
     * Only use this method when absolutely certain that all data in the directory is safe to
     * delete.</p>
     *
     * <p>The deletion process:</p>
     * <ul>
     *   <li>For regular files: Directly deletes the file</li>
     *   <li>For directories: Walks the directory tree in reverse order (bottom-up)</li>
     *   <li>Handles symbolic links without following them</li>
     *   <li>Attempts to delete each file/directory only once</li>
     * </ul>
     *
     * <p>Error handling:</p>
     * <ul>
     *   <li>Logs individual file deletion failures but continues processing</li>
     *   <li>Returns false if any deletion operation fails</li>
     *   <li>Ensures cleanup of resources using try-with-resources</li>
     * </ul>
     *
     * <p>Security considerations:</p>
     * <ul>
     *   <li>Does not follow symbolic links to prevent deletion outside target directory</li>
     *   <li>Uses NIO.2 APIs for secure file operations</li>
     *   <li>Requires appropriate filesystem permissions for all operations</li>
     * </ul>
     *
     * @param wd The directory or file to delete. If it's a directory, all contents will be
     *           deleted
     *
     * @return {@code true} if all deletions were successful, {@code false} if any deletion
     * failed
     *
     * @throws SecurityException if the security manager denies access to the files
     * @see Files#walk(Path, FileVisitOption...)
     * @see Files#deleteIfExists(Path)
     */
    public static boolean removeAll(Path wd) {
        try {
            if (!Files.isDirectory(wd)) {
                return Files.deleteIfExists(wd);
            }

            AtomicBoolean success = new AtomicBoolean(true);
            try (var files = Files.walk(wd)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        if (!Files.deleteIfExists(path)) {
                            logger.error("Failed to delete: {}", path);
                            success.set(false);
                        }
                    } catch (IOException e) {
                        logger.error("Failed to delete: {}", path, e);
                        success.set(false);
                    }
                });
            }
            return success.get();
        } catch (IOException e) {
            logger.error("Failed to delete: {}", wd, e);
            return false;
        }
    }

    public static boolean secureDeleteAll(Path wd) throws IOException {
        if (!Files.isDirectory(wd)) {
            logger.info("DELETING FILE {}", wd);
            try {
                secureDelete(wd);
            } catch (IOException e) {
                logger.error("Could not delete file: {}", wd, e);
                return false;
            }
        } else {
            try (var files = Files.list(wd)) {
                boolean success = files.allMatch(FileUtil::removeAll);

                if (!success) {
                    return false;
                }

                try {
                    Files.delete(wd);
                } catch (IOException e) {
                    logger.warn("Could not delete directory: {}", wd);
                }
            }
        }
        return true;
    }

    /**
     * Sets read and write permissions for the owner only on the specified file. This is a
     * convenience method that delegates to
     * {@link #setOwnerPerm(File, boolean, boolean, boolean)}.
     *
     * <p>This method attempts to set the following permissions:</p>
     * <ul>
     *   <li>Owner Read: Enabled</li>
     *   <li>Owner Write: Enabled</li>
     *   <li>Owner Execute: Disabled</li>
     *   <li>All group and others permissions: Disabled</li>
     * </ul>
     *
     * @param f The file to modify permissions on
     *
     * @return {@code true} if the permissions were successfully modified, {@code false}
     * otherwise
     *
     * @throws SecurityException if the security manager denies access to the file
     * @see #setOwnerPerm(File, boolean, boolean, boolean)
     */
    public static boolean setOwnerRW(File f) {
        return setOwnerPerm(f, true, true, false);
    }

    /**
     * Sets read, write, and execute permissions for the owner only on the specified file. This
     * is a convenience method that delegates to
     * {@link #setOwnerPerm(File, boolean, boolean, boolean)}.
     *
     * <p>This method attempts to set the following permissions:</p>
     * <ul>
     *   <li>Owner Read: Enabled</li>
     *   <li>Owner Write: Enabled</li>
     *   <li>Owner Execute: Enabled</li>
     *   <li>All group and others permissions: Disabled</li>
     * </ul>
     *
     * @param f The file to modify permissions on
     *
     * @return {@code true} if the permissions were successfully modified, {@code false}
     * otherwise
     *
     * @throws SecurityException if the security manager denies access to the file
     * @see #setOwnerPerm(File, boolean, boolean, boolean)
     */
    public static boolean setOwnerRWX(File f) {
        return setOwnerPerm(f, true, true, true);
    }

    public static void secureDelete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        long size = Files.size(path);
        if (size > 0) {
            try (var channel = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
            )) {
                logger.info("Securely deleting {} which is of length {}bytes...", path, size);
                channel.position(0);
                // Random data first.
                fill(Channels.newOutputStream(channel), size);
                channel.force(false);
            }
        }
        Files.delete(path);
    }

    /**
     * Sets specific owner-only permissions on the specified file or directory. This is a
     * convenience method that delegates to
     * {@link #setOwnerPerm(Path, boolean, boolean, boolean)}.
     *
     * @param f The file to modify permissions on
     * @param r If {@code true}, enable read permission for owner
     * @param w If {@code true}, enable write permission for owner
     * @param x If {@code true}, enable execute permission for owner
     *
     * @return {@code true} if the permissions were successfully modified, {@code false}
     * otherwise
     *
     * @throws SecurityException if the security manager denies access to the file
     * @see #setOwnerPerm(Path, boolean, boolean, boolean)
     * @see PosixFilePermission
     */
    public static boolean setOwnerPerm(File f, boolean r, boolean w, boolean x) {
        return setOwnerPerm(f.toPath(), r, w, x);
    }

    /**
     * Sets specific owner-only permissions on the specified file or directory. This method
     * removes all group and others permissions, leaving only the specified owner permissions.
     *
     * <p>Permission bits that can be set:</p>
     * <ul>
     *   <li>Read (r): Controls ability to read file contents or list directory contents</li>
     *   <li>Write (w): Controls ability to modify file contents or create/delete files in
     *   directory</li>
     *   <li>Execute (x): Controls ability to execute file or traverse directory</li>
     * </ul>
     *
     * <p><strong>Platform considerations:</strong></p>
     * <ul>
     *   <li>On POSIX systems: Uses native file permissions</li>
     *   <li>On Windows: Attempts to map to equivalent ACL permissions</li>
     *   <li>On other systems: May have limited functionality</li>
     * </ul>
     *
     * @param path The path to modify permissions on
     * @param r    If {@code true}, enable read permission for owner
     * @param w    If {@code true}, enable write permission for owner
     * @param x    If {@code true}, enable execute permission for owner
     *
     * @return {@code true} if the permissions were successfully modified, {@code false}
     * otherwise
     *
     * @throws SecurityException if the security manager denies access to the file
     * @see PosixFilePermission
     * @see Files#setPosixFilePermissions(Path, Set)
     */
    public static boolean setOwnerPerm(Path path, boolean r, boolean w, boolean x) {
        try {
            // Clear all existing permissions first
            Files.setPosixFilePermissions(path, Set.of());

            // Build the new permission set
            Set<PosixFilePermission> perms = new HashSet<>();
            if (r) {
                perms.add(PosixFilePermission.OWNER_READ);
            }
            if (w) {
                perms.add(PosixFilePermission.OWNER_WRITE);
            }
            if (x) {
                perms.add(PosixFilePermission.OWNER_EXECUTE);
            }

            // Set the new permissions
            Files.setPosixFilePermissions(path, perms);

            // Verify the permissions were set correctly
            Set<PosixFilePermission> actualPerms = Files.getPosixFilePermissions(path);
            return actualPerms.equals(perms);

        } catch (UnsupportedOperationException e) {
            // Fall back to legacy method for non-POSIX systems (e.g., Windows)
            boolean success = true;
            var f = path.toFile();

            // Set readable permission
            success &= f.setReadable(false, false);
            success &= f.setReadable(r, true);

            // Set writable permission
            success &= f.setWritable(false, false);
            success &= f.setWritable(w, true);

            // Set executable permission
            success &= f.setExecutable(false, false);
            success &= f.setExecutable(x, true);

            return success;

        } catch (IOException e) {
            logger.error("Failed to set permissions on {}: {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Compares two files for content equality. This is a convenience method that delegates to
     * {@link #equals(Path, Path)}.
     *
     * @param a The first file to compare
     * @param b The second file to compare
     *
     * @return {@code true} if both files exist and have identical content, {@code false}
     * otherwise
     *
     * @throws SecurityException if a security manager exists and denies read access to either
     *                           file
     * @see #equals(Path, Path)
     */
    public static boolean equals(File a, File b) {
        return equals(a.toPath(), b.toPath());
    }

    /**
     * Compares two paths for content equality using efficient NIO.2 operations.
     *
     * <p>This method performs a thorough comparison of two files:</p>
     * <ul>
     *   <li>Reference equality check (same file)</li>
     *   <li>Existence check for both files</li>
     *   <li>File size comparison</li>
     *   <li>Byte-by-byte content comparison</li>
     * </ul>
     *
     * <p><strong>Performance considerations:</strong></p>
     * <ul>
     *   <li>Uses buffered reading for efficient comparison of large files</li>
     *   <li>Stops comparison at first difference found</li>
     *   <li>Performs size check before content comparison to avoid unnecessary reading</li>
     * </ul>
     *
     * <p><strong>Special cases:</strong></p>
     * <ul>
     *   <li>Returns {@code false} if either file doesn't exist</li>
     *   <li>Returns {@code true} if both paths point to the same file</li>
     *   <li>Returns {@code false} if the files have different sizes</li>
     * </ul>
     *
     * @param pathA The first path to compare
     * @param pathB The second path to compare
     *
     * @return {@code true} if both files exist and have identical content, {@code false}
     * otherwise
     *
     * @throws SecurityException if a security manager exists and denies read access to either
     *                           file
     * @see Files#size(Path)
     * @see Files#isSameFile(Path, Path)
     */
    public static boolean equals(Path pathA, Path pathB) {
        if (pathA == pathB) {
            return true;
        }

        try {
            // Try to compare real paths (resolves symbolic links)
            try {
                Path realA = pathA.toRealPath();
                Path realB = pathB.toRealPath();
                return realA.equals(realB);
            } catch (IOException e) {
                // If we can't get real paths (e.g., file doesn't exist),
                // fall back to normalized absolute paths
                Path normalA = pathA.toAbsolutePath().normalize();
                Path normalB = pathB.toAbsolutePath().normalize();
                return normalA.equals(normalB);
            }
        } catch (SecurityException e) {
            // Fall back to original implementation if security manager prevents access
            if (pathA.equals(pathB)) {
                return true;
            }

            // Last resort: compare canonical files
            var canonA = getCanonicalFile(pathA);
            var canonB = getCanonicalFile(pathB);
            return canonA.equals(canonB);
        }
    }

    /**
     * Creates a temporary file with the specified prefix and suffix in the given directory.
     * The created file will be deleted when the JVM exits.
     *
     * <p>This method is similar to {@link #createTempFile(String, String, Path)} but accepts
     * a {@link File} parameter for the directory and allows null values for all parameters.
     *
     * @param prefix    The prefix string to be used in generating the file's name; must be at
     *                  least three characters long
     * @param suffix    The suffix string to be used in generating the file's name; may be
     *                  null, in which case ".tmp" is used
     * @param directory The directory in which the file is to be created. If null, uses the
     *                  default temporary directory
     *
     * @return A newly created temporary File object
     *
     * @throws IOException              If a file could not be created due to:
     *                                  <ul>
     *                                  <li>Insufficient permissions in the target
     *                                  directory</li>
     *                                  <li>Target directory does not exist</li>
     *                                  <li>Disk space is exhausted</li>
     *                                  </ul>
     * @throws IllegalArgumentException If prefix is shorter than 3 characters or the specified
     *                                  directory is not a directory
     * @throws SecurityException        If a security manager exists and denies write access to
     *                                  the file
     * @see Files#createTempFile
     * @see File#deleteOnExit()
     */
    public static File createTempFile(String prefix, String suffix, File directory)
        throws IOException {
        if (directory == null) {
            directory = new File(".");
        }
        return createTempFile(prefix, suffix, directory.toPath()).toFile();
    }

    public static boolean copyFile(Path copyFrom, Path copyTo) {
        try {
            Files.deleteIfExists(copyTo);
        } catch (IOException e) {
            logger.error("Failed to delete existing file {}", copyTo, e);
        }
        boolean copyFromExecutable = Files.isExecutable(copyFrom);
        var outBucket = new RegularFile(copyTo, false, true, false, false);
        var inBucket = new RegularFile(copyFrom, true, false, false, false);
        try {
            BucketTools.copy(inBucket, outBucket);
        } catch (IOException e) {
            logger.error("Unable to copy from {} to {}", copyFrom, copyTo);
            return false;
        }
        if (copyFromExecutable && !Files.isExecutable(copyTo)) {
            try {
                var perms = Files.getPosixFilePermissions(copyTo);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(copyTo, perms);
            } catch (IOException | UnsupportedOperationException e) {
                logger.error(
                    "Unable to preserve executable bit when copying{} to {} - you " +
                    "may need to make itexecutable!", copyFrom, copyTo
                );
                // return false; ??? FIXME debatable.
            }
        }
        return true;
    }

    /**
     * Write hard to identify random data to the OutputStream. Does not drain the global secure
     * random number generator, and is significantly faster than it.
     *
     * @param os     The stream to write to.
     * @param length The number of bytes to write.
     *
     * @throws IOException If unable to write to the stream.
     */
    public static void fill(OutputStream os, long length) throws IOException {
        long remaining = length;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = 0;
        while ((remaining == -1) || (remaining > 0)) {
            synchronized (FileUtil.class) {
                if (cis == null || cisCounter > Long.MAX_VALUE / 2) {
                    // Reset it well before the birthday paradox (note this is actually
                    // counting bytes).
                    byte[] key = new byte[16];
                    byte[] iv = new byte[16];
                    Global.SECURE_RANDOM.nextBytes(key);
                    Global.SECURE_RANDOM.nextBytes(iv);
                    AESFastEngine e = new AESFastEngine();
                    SICBlockCipher ctr = new SICBlockCipher(e);
                    ctr.init(true, new ParametersWithIV(new KeyParameter(key), iv));
                    cis = new CipherInputStream(zis, new BufferedBlockCipher(ctr));
                    cisCounter = 0;
                }
                read = cis.read(
                    buffer,
                    0,
                    ((remaining > BUFFER_SIZE) || (remaining == -1)) ? BUFFER_SIZE :
                        (int) remaining
                );
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
     * Creates a temporary file with the specified prefix and suffix in the given directory.
     * The created file will be deleted when the JVM exits.
     *
     * <p>This method provides control over the location of the temporary file.
     * The specified directory must exist and be writable.
     *
     * @param prefix    The prefix string to be used in generating the file's name; must be at
     *                  least three characters long
     * @param suffix    The suffix string to be used in generating the file's name; may be
     *                  null, in which case ".tmp" is used
     * @param directory The directory in which the file is to be created. If null, uses the
     *                  default temporary directory
     *
     * @return A newly created temporary File object
     *
     * @throws IOException              If a file could not be created due to:
     *                                  <ul>
     *                                  <li>Insufficient permissions in the target
     *                                  directory</li>
     *                                  <li>Target directory does not exist</li>
     *                                  <li>Disk space is exhausted</li>
     *                                  </ul>
     * @throws IllegalArgumentException If prefix is shorter than 3 characters or the specified
     *                                  directory is not a directory
     * @throws SecurityException        If a security manager exists and denies write access to
     *                                  the file
     * @see Files#createTempFile
     * @see File#deleteOnExit()
     */
    public static Path createTempFile(
        @Nullable String prefix, @Nullable String suffix, @Nullable Path directory)
        throws IOException {
        // Validate inputs
        if (prefix == null) {
            prefix = "";
        }

        // Ensure prefix meets minimum length requirement
        if (prefix.length() < 3) {
            prefix = prefix + "-TMP";
        }

        // Use current directory if no directory specified
        if (directory == null) {
            directory = Path.of(".");
        }

        try {
            // Ensure directory exists and is writable
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            if (!Files.isWritable(directory)) {
                throw new IOException("Directory is not writable: " + directory);
            }

            // Create temp file with restricted permissions
            return Files.createTempFile(
                directory,
                prefix,
                suffix,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(
                    "rw-------"))
            );

        } catch (UnsupportedOperationException e) {
            // Fall back to basic temp file creation on non-POSIX systems
            return Files.createTempFile(directory, prefix, suffix);
        }
    }

    /**
     * Compares two input streams for content equality up to a specified size.
     *
     * <p>This method reads and compares the streams byte by byte until either:</p>
     * <ul>
     *   <li>The specified size limit is reached</li>
     *   <li>One or both streams end</li>
     *   <li>A difference is found</li>
     * </ul>
     *
     * <p><strong>Important:</strong> This method will consume data from both input streams
     * up to the specified size. The streams are not reset after reading.</p>
     *
     * <h3>Performance Considerations:</h3>
     * <ul>
     *   <li>Uses buffered reading for efficient comparison of large streams</li>
     *   <li>Stops immediately when a difference is found</li>
     *   <li>Memory efficient - only allocates a small buffer regardless of stream size</li>
     * </ul>
     *
     * <h3>Edge Cases:</h3>
     * <ul>
     *   <li>If both streams are null, returns true</li>
     *   <li>If only one stream is null, returns false</li>
     *   <li>If size is 0 or negative, returns true</li>
     *   <li>If streams have different lengths within the size limit, returns false</li>
     * </ul>
     *
     * @param a    the first input stream to compare
     * @param b    the second input stream to compare
     * @param size the maximum number of bytes to compare
     *
     * @return true if both streams contain identical content up to the specified size, false
     * otherwise
     *
     * @throws IOException if an I/O error occurs while reading either stream
     * @see InputStream#read()
     */
    public static boolean equalStreams(
        @Nullable InputStream a, @Nullable InputStream b, long size) throws IOException {
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
            byte[] bufferA = new byte[Math.min(
                BUFFER_SIZE,
                Math.max(8192, (int) (size / 1024))
            )];
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
                    Arrays.copyOfRange(bufferA, 0, readA),
                    Arrays.copyOfRange(bufferB, 0, readB)
                )) {
                    return false;
                }

                remaining -= readA;
            }

            return true;
        }
    }

    /**
     * Determines whether a character in a filename should be replaced based on operating
     * system rules.
     *
     * <p>This method evaluates a single character against platform-specific filename
     * restrictions and custom validation rules.</p>
     *
     * <h3>Operating System Rules:</h3>
     * <ul>
     *   <li><strong>Windows:</strong> Restricts characters like {@code <>:"/\|?*} and control
     *   characters</li>
     *   <li><strong>macOS:</strong> Restricts colon {@code :} and forward slash {@code /}</li>
     *   <li><strong>Linux:</strong> Restricts forward slash {@code /} and null character</li>
     *   <li><strong>Unknown OS:</strong> Applies the most restrictive combination of
     *   rules</li>
     * </ul>
     *
     * <p>The method also handles special cases:</p>
     * <ul>
     *   <li>Control characters (ASCII 0-31) are always replaced</li>
     *   <li>Characters specified in {@code extraChars} are preserved</li>
     *   <li>Characters in {@code reservedChars} are always replaced</li>
     *   <li>Platform-specific path separators are always replaced</li>
     * </ul>
     *
     * @param c             The character to evaluate
     * @param targetOS      The target operating system for which to check filename validity
     * @param extraChars    Additional characters to preserve (not replace)
     * @param reservedChars Characters that should always be replaced
     *
     * @return true if the character should be replaced, false if it should be preserved
     */
    private static boolean shouldReplace(
        int c, OperatingSystem targetOS, String extraChars, String reservedChars) {
        // Check for control characters and whitespace
        if (Character.isISOControl(c) || Character.isWhitespace(c)) {
            return true;
        }

        // Check extra chars
        if (extraChars.indexOf(c) != -1) {
            return true;
        }

        // Check reserved chars
        if (reservedChars.indexOf(c) != -1) {
            return true;
        }

        // Check OS-specific restrictions
        return switch (targetOS) {
            case WINDOWS -> c < 32 ||
                            StringValidityChecker.isWindowsReservedPrintableFilenameCharacter((char) c);
            case MACOS ->
                StringValidityChecker.isMacOSReservedPrintableFilenameCharacter((char) c);
            case LINUX, FREEBSD, GENERIC_UNIX ->
                StringValidityChecker.isUnixReservedPrintableFilenameCharacter((char) c);
            case UNKNOWN -> c < 32 ||
                            StringValidityChecker.isWindowsReservedPrintableFilenameCharacter((char) c) ||
                            StringValidityChecker.isMacOSReservedPrintableFilenameCharacter((char) c) ||
                            StringValidityChecker.isUnixReservedPrintableFilenameCharacter((char) c);
        };
    }

    /**
     * Determines an appropriate replacement character for invalid filename characters based on
     * the provided set of extra allowed characters.
     *
     * <p>The method selects a replacement character that is:
     * <ul>
     *   <li>Safe to use in filenames across all major operating systems</li>
     *   <li>Not included in the set of extra allowed characters</li>
     *   <li>Visually distinct to make modified filenames easily identifiable</li>
     * </ul>
     *
     * <p>The selection process follows this priority order:
     * <ol>
     *   <li>Space character ( ) if not in extraChars</li>
     *   <li>Underscore (_) if not in extraChars</li>
     *   <li>Hyphen (-) if not in extraChars</li>
     * </ol>
     * <p>
     * If no suitable replacement character can be found (all potential replacements are in
     * extraChars),
     * an IllegalStateException is thrown to prevent creation of invalid filenames.
     *
     * @param extraChars A string containing additional characters that should be considered
     *                   valid and therefore not used as replacement characters. May be null or
     *                   empty.
     *
     * @return A character suitable for replacing invalid filename characters
     *
     * @throws IllegalStateException if no suitable replacement character can be found
     * @see #sanitizeFileName(String, OperatingSystem, String)
     */
    private static char determineReplacementChar(String extraChars) {
        if (!extraChars.contains(" ")) {
            return ' ';
        }
        if (!extraChars.contains("_")) {
            return '_';
        }
        if (!extraChars.contains("-")) {
            return '-';
        }
        throw new IllegalArgumentException("No suitable replacement character available");
    }

    /**
     * Checks if a filename matches any Windows reserved names or patterns.
     *
     * <p>Windows has specific restrictions on filenames that include:</p>
     * <ul>
     *   <li>Reserved device names (e.g., CON, PRN, AUX, NUL)</li>
     *   <li>Reserved names followed by an extension (e.g., CON.txt)</li>
     *   <li>Legacy device names (COM1-COM9, LPT1-LPT9)</li>
     * </ul>
     *
     * <p>The check is case-insensitive as Windows treats filenames in a case-insensitive
     * manner.
     * For example, both "CON" and "con" are considered reserved names.</p>
     *
     * <p>Examples of reserved names:</p>
     * <pre>
     *   CON  - Console
     *   PRN  - Printer
     *   AUX  - Auxiliary device
     *   NUL  - Null device
     *   COM1 - Serial communication port
     *   LPT1 - Line printer terminal
     * </pre>
     *
     * @param filename the filename to check, without any path components
     *
     * @return {@code true} if the filename matches a Windows reserved name, {@code false}
     * otherwise
     */
    private static boolean isWindowsReservedName(String filename) {
        return filename.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\.[^.]*)?$");
    }

    /**
     * Calculates the actual storage space used by a filename on the filesystem. This method
     * takes into account:
     * <ul>
     *   <li>Operating system-specific filesystem implementations</li>
     *   <li>Character encoding overhead (UTF-8/UTF-16)</li>
     *   <li>Filesystem metadata storage requirements</li>
     * </ul>
     *
     * @param path The {@link Path} object representing the file whose name is being analyzed
     *
     * @return The total number of bytes used to store the filename on the filesystem,
     * including metadata metadata and alignment padding
     *
     * @implNote For Windows (NTFS), assumes UTF-16 encoding with 100 bytes base metadata. For
     * macOS (HFS+/APFS), assumes UTF-8 encoding with 248 bytes header. For other systems, uses
     * the larger of UTF-8 and UTF-16 encodings with 100 bytes overhead.
     * @see #roundup_2n(long, int)
     */
    private static long getFilenameUsage(Path path) {
        String filename = path.getFileName().toString();
        int nameLength = switch (DETECTED_OS) {
            case WINDOWS -> {
                // NTFS uses Unicode (UTF-16) for filenames with additional metadata
                int baseLength = 100; // Base metadata size
                yield baseLength + filename.getBytes(StandardCharsets.UTF_16).length;
            }
            case MACOS -> {
                // HFS+/APFS uses UTF-8 with additional metadata
                int baseLength = 248; // HFS+/APFS header size
                yield baseLength + filename.getBytes(StandardCharsets.UTF_8).length;
            }
            default -> {
                // Generic Unix-like systems (ext4, etc.)
                int baseLength = 100; // Conservative estimate for inode overhead
                yield baseLength + Math.max(
                    filename.getBytes(StandardCharsets.UTF_16).length,
                    filename.getBytes(StandardCharsets.UTF_8).length
                );
            }
        };

        return roundup_2n(nameLength, 512);
    }

    /**
     * Detects the current operating system by analyzing system properties and filesystem
     * characteristics.
     *
     * <p>The detection process follows these steps:</p>
     * <ol>
     *   <li>Checks the "os.name" system property for known operating system names</li>
     *   <li>Uses case-insensitive substring matching for common OS identifiers</li>
     *   <li>Falls back to filesystem separator analysis if the OS name is not recognized</li>
     * </ol>
     *
     * <p>Supported operating systems:</p>
     * <ul>
     *   <li>Windows</li>
     *   <li>macOS</li>
     *   <li>Linux</li>
     *   <li>FreeBSD</li>
     *   <li>Generic Unix-like systems</li>
     * </ul>
     *
     * @return The detected {@link OperatingSystem} enum value representing the current
     * operating system. Returns {@code OperatingSystem.UNKNOWN} if the system cannot be
     * identified.
     *
     * @implNote This method uses pattern matching in switch expressions (requires Java 17+).
     * The fallback detection uses the filesystem path separator character to distinguish
     * between Unix-like systems ('/') and others.
     * @todo This method should be relocated to a more appropriate utility or system-related
     * class
     * @see System#getProperty(String)
     * @see OperatingSystem
     */
    private static OperatingSystem detectOperatingSystem() { // TODO: Move to the proper class
        return switch (System.getProperty("os.name").toLowerCase()) {
            case String s when s.contains("win") -> OperatingSystem.WINDOWS;
            case String s when s.contains("mac") -> OperatingSystem.MACOS;
            case String s when s.contains("linux") -> OperatingSystem.LINUX;
            case String s when s.contains("freebsd") -> OperatingSystem.FREEBSD;
            case String s when s.contains("unix") -> OperatingSystem.GENERIC_UNIX;
            default -> Path.of("").getFileSystem().getSeparator().equals("/") ?
                OperatingSystem.GENERIC_UNIX : OperatingSystem.UNKNOWN;
        };
    }

    /**
     * Detects the CPU architecture of the current system by analyzing system properties.
     *
     * <p>Supports detection of the following CPU architectures:</p>
     * <table>
     *   <tr><th>Architecture</th><th>Identifiers</th></tr>
     *   <tr>
     *     <td>32-bit x86</td>
     *     <td>x86, i386, i486, i586, i686, etc.</td>
     *   </tr>
     *   <tr>
     *     <td>64-bit x86</td>
     *     <td>amd64, x86_64, x86-64, em64t</td>
     *   </tr>
     *   <tr>
     *     <td>ARM</td>
     *     <td>arm (32-bit), aarch64 (64-bit)</td>
     *   </tr>
     *   <tr>
     *     <td>PowerPC</td>
     *     <td>ppc, powerpc (32-bit), ppc64 (64-bit)</td>
     *   </tr>
     *   <tr>
     *     <td>Intel Itanium</td>
     *     <td>ia64</td>
     *   </tr>
     *   <tr>
     *     <td>RISC-V</td>
     *     <td>riscv64 (64-bit)</td>
     *   </tr>
     * </table>
     *
     * @return The detected {@link CPUArchitecture} enum value representing the current CPU
     * architecture. Returns {@code CPUArchitecture.UNKNOWN} if the architecture cannot be
     * identified.
     *
     * @implNote This method uses pattern matching in switch expressions (requires Java 17+).
     * The detection is based on the "os.arch" system property value. Regular expressions are
     * used to match various architecture naming conventions.
     * @see System#getProperty(String)
     * @see CPUArchitecture
     */
    // TODO: This method should be relocated to a more appropriate system-related utility class
    private static CPUArchitecture detectCPUArchitecture() {
        return switch (System.getProperty("os.arch").toLowerCase()) {
            case String s when s.matches("x86|i386|i[3-9]86") -> CPUArchitecture.X86_32;
            case String s when s.matches("amd64|x86[-_]?64|em64t") -> CPUArchitecture.X86_64;
            case "aarch64" -> CPUArchitecture.ARM_64;
            case String s when s.startsWith("arm") -> CPUArchitecture.ARM_32;
            case String s when s.matches("ppc|powerpc") -> CPUArchitecture.PPC_32;
            case "ppc64" -> CPUArchitecture.PPC_64;
            case String s when s.startsWith("ia64") -> CPUArchitecture.IA64;
            case String s when s.contains("riscv64") -> CPUArchitecture.RISCV_64;
            default -> CPUArchitecture.UNKNOWN;
        };
    }

    /**
     * Rounds up a value to the nearest multiple of a power-of-two block size. This method is
     * used for calculating actual storage space usage on block-based filesystems.
     *
     * @param val       The value to round up
     * @param blocksize The block size to align to (must be a power of 2)
     *
     * @return The value rounded up to the nearest multiple of blocksize
     *
     * @throws IllegalArgumentException implicitly if blocksize is not a power of 2
     * @implNote Uses bitwise operations for efficient calculation: 1. Creates a mask from
     * blocksize 2. Adds (blocksize - 1) to handle rounding 3. Uses bitwise AND with inverted
     * mask to align to block boundary
     */
    private static long roundup_2n(long val, int blocksize) {
        int mask = blocksize - 1;
        return (val + mask) & ~mask;
    }

}
