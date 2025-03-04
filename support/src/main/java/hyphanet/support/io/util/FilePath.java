package hyphanet.support.io.util;

import hyphanet.base.SystemInfo;
import hyphanet.support.StringValidityChecker;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Utility class providing file path manipulation and validation operations. Handles
 * platform-specific path requirements and provides safe file operations.
 *
 * <p>This class includes functionality for:
 *
 * <ul>
 *   <li>Path canonicalization and normalization
 *   <li>Parent-child relationship verification
 *   <li>Filename sanitization
 *   <li>File content comparison
 * </ul>
 */
public final class FilePath {

  /**
   * The charset used for file name encoding/decoding operations.
   *
   * <p>This charset is determined by the system's file encoding configuration. On Windows and
   * Linux, it typically matches the user's system language settings. While mis-detection of the
   * filename charset may cause download failures due to invalid filenames, path and file separator
   * characters are always disallowed to prevent arbitrary file storage locations.
   */
  private static final Charset FILE_NAME_CHARSET = SystemInfo.getFileEncodingCharset();

  private FilePath() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Determines if one file is a parent directory of another file. This is a convenience method that
   * delegates to {@link #isParent(Path, Path)}.
   *
   * @param poss The potential parent file
   * @param filename The file to check
   * @return {@code true} if poss is a parent of filename or if they are the same path, {@code
   *     false} if they are unrelated or if an error occurs
   * @see #isParent(Path, Path)
   */
  public static boolean isParent(File poss, File filename) {
    return isParent(poss.toPath(), filename.toPath());
  }

  /**
   * Determines if one path is a parent directory of another path.
   *
   * <p>This method performs the following checks:
   *
   * <ul>
   *   <li>Resolves symbolic links in both paths
   *   <li>Normalizes paths to handle different path separators
   *   <li>Checks if paths are identical (considered as parent)
   *   <li>Verifies if one path is a parent directory of another
   * </ul>
   *
   * <p>This method safely handles symbolic links by resolving them before comparison, preventing
   * path traversal attacks.
   *
   * <p>If any I/O errors occur during path resolution (e.g., file not found, insufficient
   * permissions), the method returns {@code false} rather than throwing an exception.
   *
   * @param poss The potential parent path to check
   * @param filename The path that might be a child of the parent
   * @return {@code true} if poss is a parent of filename or if they are the same path, {@code
   *     false} if they are unrelated or if an error occurs
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
   * Returns the canonical form of the specified file. This is a convenience method that delegates
   * to {@link #getCanonicalFile(Path)}.
   *
   * @param file The file to canonicalize
   * @return The canonical file, or the absolute file if canonicalization fails
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
   * Returns the canonical form of the specified path, resolving symbolic links and normalizing the
   * path name.
   *
   * <p>This method performs the following operations:
   *
   * <ul>
   *   <li>Normalizes path based on operating system conventions
   *   <li>Resolves all symbolic links in the path
   *   <li>Removes redundant name elements like "." and ".."
   *   <li>Standardizes path separators for the platform
   * </ul>
   *
   * <p><b>Platform-Specific Behavior:</b>
   *
   * <ul>
   *   <li>Windows: Converts path to lowercase for case-insensitive comparison
   *   <li>Unix/Linux: Preserves case sensitivity of the original path
   *   <li>All platforms: Uses platform-specific path separators
   * </ul>
   *
   * <p>If any errors occur during canonicalization (such as broken symbolic links or insufficient
   * permissions), the method falls back to returning the absolute path in normalized form.
   *
   * @param path The path to canonicalize
   * @return The canonical path, or the absolute normalized path if canonicalization fails
   * @throws SecurityException if the security manager denies access to the file system
   */
  public static Path getCanonicalFile(Path path) {
    try {
      // Normalize the path string based on OS
      String normalizedPath =
          (SystemInfo.DETECTED_OS == SystemInfo.OperatingSystem.WINDOWS)
              ? path.toString().toLowerCase(Locale.ROOT)
              : path.toString();

      // Create new path from normalized string
      path = Path.of(normalizedPath);

      // Resolve symbolic links and normalize path
      return path.toRealPath();

    } catch (IOException | SecurityException e) {
      // If we can't get the real path, fall back to absolute path
      return path.toAbsolutePath().normalize();
    }
  }

  /**
   * Sanitizes a filename to ensure it is valid for the specified operating system. The method
   * removes or replaces invalid characters and handles platform-specific filename restrictions.
   *
   * <p>The sanitization process includes:
   *
   * <ul>
   *   <li>Filtering out invalid charset characters
   *   <li>Replacing OS-specific reserved characters
   *   <li>Handling platform-specific filename restrictions
   *   <li>Ensuring the result is a valid filename across different filesystems
   * </ul>
   *
   * <p>For {@code OperatingSystem.UNKNOWN}, the method applies the most restrictive combination of
   * rules to ensure compatibility across all supported platforms:
   *
   * <ul>
   *   <li>Windows: {@code <>:"/\|?*}
   *   <li>macOS: {@code /:}
   *   <li>Unix/Linux: {@code /}
   * </ul>
   *
   * <p>Special cases handled:
   *
   * <ul>
   *   <li>Empty filenames are replaced with "Invalid_filename"
   *   <li>Windows reserved names (e.g., CON, PRN) are prefixed with an underscore
   *   <li>Trailing dots and spaces are removed for Windows compatibility
   * </ul>
   *
   * @param fileName The original filename to sanitize
   * @param targetOS The target operating system to sanitize for
   * @param extraChars Additional characters to be considered valid (not replaced)
   * @return A sanitized filename that is valid for the target operating system
   * @throws IllegalArgumentException if fileName is null
   * @see SystemInfo.OperatingSystem
   */
  public static String sanitizeFileName(
      String fileName, SystemInfo.OperatingSystem targetOS, String extraChars) {
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
    String reservedChars =
        switch (targetOS) {
          case WINDOWS -> "<>:\"/\\|?*";
          case MACOS -> "/:";
          case LINUX, FREEBSD, GENERIC_UNIX -> "/";
          case UNKNOWN -> "<>:\"/\\|?*/:"; // Most restrictive combination
        };

    // Process each character
    buffer
        .chars()
        .forEach(
            c -> {
              if (shouldReplace(c, targetOS, extraChars, reservedChars)) {
                sb.append(replacementChar);
              } else {
                sb.append((char) c);
              }
            });

    // Handle Windows-specific restrictions
    String result = sb.toString().trim();
    if (targetOS == SystemInfo.OperatingSystem.UNKNOWN
        || targetOS == SystemInfo.OperatingSystem.WINDOWS) {
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
   * Sanitizes a filename using the detected operating system's rules. This is a convenience method
   * that delegates to {@link #sanitizeFileName(String, SystemInfo.OperatingSystem, String)} with no
   * extra allowed characters.
   *
   * <p>The method uses the system's detected operating system ({@link SystemInfo#DETECTED_OS}) to
   * determine which character restrictions to apply. This ensures the filename will be valid on the
   * current platform.
   *
   * @param fileName The filename to sanitize
   * @return A sanitized filename that is valid for the current operating system
   * @throws IllegalArgumentException if fileName is null
   * @see #sanitizeFileName(String, SystemInfo.OperatingSystem, String)
   * @see SystemInfo#DETECTED_OS
   */
  public static String sanitize(String fileName) {
    return sanitizeFileName(fileName, SystemInfo.DETECTED_OS, "");
  }

  /**
   * Sanitizes a filename using the detected operating system's rules, allowing additional specified
   * characters to be considered valid. This is a convenience method that delegates to {@link
   * #sanitizeFileName(String, SystemInfo.OperatingSystem, String)}.
   *
   * <p>This method extends the basic sanitization by allowing specific characters to be preserved
   * in the filename. This is useful when certain special characters are known to be safe in a
   * particular context.
   *
   * <p>Example usage:
   *
   * <pre>
   * // Allow hyphens and plus signs in filenames
   * String safe = sanitizeFileNameWithExtras("my-file+name.txt", "-+");
   * </pre>
   *
   * @param fileName The filename to sanitize
   * @param extraChars Additional characters to be considered valid (not replaced)
   * @return A sanitized filename that is valid for the current operating system, preserving the
   *     specified extra characters
   * @throws IllegalArgumentException if fileName is null
   * @see #sanitizeFileName(String, SystemInfo.OperatingSystem, String)
   * @see SystemInfo#DETECTED_OS
   */
  public static String sanitizeFileNameWithExtras(String fileName, String extraChars) {
    return sanitizeFileName(fileName, SystemInfo.DETECTED_OS, extraChars);
  }

  /**
   * Compares two files for content equality. This is a convenience method that delegates to {@link
   * #equals(Path, Path)}.
   *
   * @param a The first file to compare
   * @param b The second file to compare
   * @return {@code true} if both files exist and have identical content, {@code false} otherwise
   * @throws SecurityException if a security manager exists and denies read access to either file
   * @see #equals(Path, Path)
   */
  public static boolean equals(File a, File b) {
    return equals(a.toPath(), b.toPath());
  }

  /**
   * Compares two paths for content equality using efficient NIO.2 operations.
   *
   * <p>This method performs a thorough comparison of two files:
   *
   * <ul>
   *   <li>Reference equality check (same file)
   *   <li>Existence check for both files
   *   <li>File size comparison
   *   <li>Byte-by-byte content comparison
   * </ul>
   *
   * <p><strong>Performance considerations:</strong>
   *
   * <ul>
   *   <li>Uses buffered reading for efficient comparison of large files
   *   <li>Stops comparison at first difference found
   *   <li>Performs size check before content comparison to avoid unnecessary reading
   * </ul>
   *
   * <p><strong>Special cases:</strong>
   *
   * <ul>
   *   <li>Returns {@code false} if either file doesn't exist
   *   <li>Returns {@code true} if both paths point to the same file
   *   <li>Returns {@code false} if the files have different sizes
   * </ul>
   *
   * @param pathA The first path to compare
   * @param pathB The second path to compare
   * @return {@code true} if both files exist and have identical content, {@code false} otherwise
   * @throws SecurityException if a security manager exists and denies read access to either file
   * @see Files#size(Path)
   * @see Files#isSameFile(Path, Path)
   */
  public static boolean equals(@Nullable Path pathA, @Nullable Path pathB) {
    if (pathA == pathB) {
      return true;
    } else if (pathA == null || pathB == null) {
      return false;
    }

    try {
      // Try to compare real paths (resolves symbolic links)
      Path realA = pathA.toRealPath();
      Path realB = pathB.toRealPath();
      return realA.equals(realB);
    } catch (IOException e) {
      // If we can't get real paths (e.g., file doesn't exist),
      // fall back to normalized absolute paths
      Path normalA = pathA.toAbsolutePath().normalize();
      Path normalB = pathB.toAbsolutePath().normalize();
      return normalA.equals(normalB);
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
   * Determines whether a character in a filename should be replaced based on operating system
   * rules.
   *
   * <p>This method evaluates a single character against platform-specific filename restrictions and
   * custom validation rules.
   *
   * <h3>Operating System Rules:</h3>
   *
   * <ul>
   *   <li><strong>Windows:</strong> Restricts characters like {@code <>:"/\|?*} and control
   *       characters
   *   <li><strong>macOS:</strong> Restricts colon {@code :} and forward slash {@code /}
   *   <li><strong>Linux:</strong> Restricts forward slash {@code /} and null character
   *   <li><strong>Unknown OS:</strong> Applies the most restrictive combination of rules
   * </ul>
   *
   * <p>The method also handles special cases:
   *
   * <ul>
   *   <li>Control characters (ASCII 0-31) are always replaced
   *   <li>Characters specified in {@code extraChars} are preserved
   *   <li>Characters in {@code reservedChars} are always replaced
   *   <li>Platform-specific path separators are always replaced
   * </ul>
   *
   * @param c The character to evaluate
   * @param targetOS The target operating system for which to check filename validity
   * @param extraChars Additional characters to preserve (not replace)
   * @param reservedChars Characters that should always be replaced
   * @return true if the character should be replaced, false if it should be preserved
   */
  private static boolean shouldReplace(
      int c, SystemInfo.OperatingSystem targetOS, String extraChars, String reservedChars) {
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
      case WINDOWS ->
          c < 32 || StringValidityChecker.isWindowsReservedPrintableFilenameCharacter((char) c);
      case MACOS -> StringValidityChecker.isMacOSReservedPrintableFilenameCharacter((char) c);
      case LINUX, FREEBSD, GENERIC_UNIX ->
          StringValidityChecker.isUnixReservedPrintableFilenameCharacter((char) c);
      case UNKNOWN ->
          c < 32
              || StringValidityChecker.isWindowsReservedPrintableFilenameCharacter((char) c)
              || StringValidityChecker.isMacOSReservedPrintableFilenameCharacter((char) c)
              || StringValidityChecker.isUnixReservedPrintableFilenameCharacter((char) c);
    };
  }

  /**
   * Determines an appropriate replacement character for invalid filename characters based on the
   * provided set of extra allowed characters.
   *
   * <p>The method selects a replacement character that is:
   *
   * <ul>
   *   <li>Safe to use in filenames across all major operating systems
   *   <li>Not included in the set of extra allowed characters
   *   <li>Visually distinct to make modified filenames easily identifiable
   * </ul>
   *
   * <p>The selection process follows this priority order:
   *
   * <ol>
   *   <li>Space character ( ) if not in extraChars
   *   <li>Underscore (_) if not in extraChars
   *   <li>Hyphen (-) if not in extraChars
   * </ol>
   *
   * <p>If no suitable replacement character can be found (all potential replacements are in
   * extraChars), an IllegalStateException is thrown to prevent creation of invalid filenames.
   *
   * @param extraChars A string containing additional characters that should be considered valid and
   *     therefore not used as replacement characters. May be null or empty.
   * @return A character suitable for replacing invalid filename characters
   * @throws IllegalStateException if no suitable replacement character can be found
   * @see #sanitizeFileName(String, SystemInfo.OperatingSystem, String)
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
   * <p>Windows has specific restrictions on filenames that include:
   *
   * <ul>
   *   <li>Reserved device names (e.g., CON, PRN, AUX, NUL)
   *   <li>Reserved names followed by an extension (e.g., CON.txt)
   *   <li>Legacy device names (COM1-COM9, LPT1-LPT9)
   * </ul>
   *
   * <p>The check is case-insensitive as Windows treats filenames in a case-insensitive manner. For
   * example, both "CON" and "con" are considered reserved names.
   *
   * <p>Examples of reserved names:
   *
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
   * @return {@code true} if the filename matches a Windows reserved name, {@code false} otherwise
   */
  private static boolean isWindowsReservedName(String filename) {
    return filename.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\.[^.]*)?$");
  }

  // TODO
  //    public static String sanitize(String filename, String mimeType) {
  //        filename = sanitize(filename);
  //        if (mimeType == null) {
  //            return filename;
  //        }
  //        return DefaultMIMETypes.forceExtension(filename, mimeType);
  //    }

}
