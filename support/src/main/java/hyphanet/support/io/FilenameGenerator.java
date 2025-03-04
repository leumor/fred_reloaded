package hyphanet.support.io;

import hyphanet.base.TimeUtil;
import hyphanet.support.io.util.FilePath;
import hyphanet.support.io.util.FileSystem;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code FilenameGenerator} class provides utility methods for generating and managing
 * temporary filenames. It ensures that generated filenames are unique and handles the creation of
 * temporary files in a specified directory. This class is designed to be secure against symlink
 * attacks and race conditions when creating temporary files.
 *
 * <p>It tracks the current temporary files settings, including the directory and prefix, and
 * provides methods to translate between IDs and filenames. Additionally, it offers functions for
 * creating temp files, which should be safe against symlink attacks and race conditions.
 *
 * <p><b>Note:</b> Consider using {@link java.io.File#createTempFile(String, String, java.io.File)}
 * as an alternative. However, using custom code might offer enhanced security if a better
 * pseudo-random number generator (PRNG) than the default one is employed. If a change to {@code
 * File.createTempFile()} is made, corresponding changes will be required in the {@code
 * TempFileBucket} class.
 *
 * @author toad
 */
public class FilenameGenerator {

  private static final Logger logger = LoggerFactory.getLogger(FilenameGenerator.class);

  /**
   * Constructs a new {@code FilenameGenerator} with the specified parameters. Initializes the
   * temporary directory and optionally wipes existing files in the directory.
   *
   * @param random the random number generator to use for generating filenames
   * @param wipeFiles {@code true} to wipe existing files in the temporary directory, {@code false}
   *     otherwise
   * @param dir the temporary directory to use. If {@code null}, the default temporary directory is
   *     used, retrieved via the system property "java.io.tmpdir"
   * @param prefix the prefix to use for generated filenames
   * @throws IOException if an I/O error occurs during directory initialization or file wiping
   */
  public FilenameGenerator(Random random, boolean wipeFiles, Path dir, String prefix)
      throws IOException {
    this.random = random;
    this.prefix = prefix;
    this.tmpDir = initializeDirectory(dir);

    if (wipeFiles) {
      wipeExistingFiles();
    }
  }

  /**
   * Generates a random filename and returns its corresponding ID. The generated filename is
   * guaranteed to be unique within the temporary directory.
   *
   * @return the ID corresponding to the generated random filename
   * @throws IOException if an I/O error occurs while creating the file
   */
  public long makeRandomFilename() throws IOException {
    while (true) {
      long randomFilename = random.nextLong();
      if (randomFilename == -1) {
        continue; // Disallowed as used for error reporting
      }

      Path path = tmpDir.resolve(prefix + Long.toHexString(randomFilename));

      try {
        Files.createFile(path);
        logger.info("Made random filename: {}", path);
        return randomFilename;
      } catch (FileAlreadyExistsException e) {
        // Try again with a different random number
      }
    }
  }

  /**
   * Returns the {@link Path} corresponding to the given ID. The ID is used to construct the
   * filename within the temporary directory.
   *
   * @param id the ID to get the path for
   * @return the {@link Path} corresponding to the ID
   */
  public Path getPath(long id) {
    return tmpDir.resolve(prefix + Long.toHexString(id));
  }

  /**
   * Creates a new random file and returns its {@link Path}. This method combines {@link
   * #makeRandomFilename()} and {@link #getPath(long)} to provide a convenient way to create a new
   * temporary file.
   *
   * @return the {@link Path} of the newly created random file
   * @throws IOException if an I/O error occurs while creating the file
   */
  public Path makeRandomFile() throws IOException {
    return getPath(makeRandomFilename());
  }

  /**
   * Returns the temporary directory used by this {@code FilenameGenerator}.
   *
   * @return the temporary directory as a {@link Path}
   */
  public Path getDir() {
    return tmpDir;
  }

  /**
   * Moves the file at the given {@code path} to a new location specified by the given {@code id} if
   * it matches the current configuration (i.e., it is in the temporary directory and starts with
   * the correct prefix). If the file does not match, it is returned as-is.
   *
   * @param path the path of the file to move
   * @param id the ID to use for the new filename
   * @return the {@link Path} of the moved file, or the original {@code path} if it was not moved
   */
  public Path maybeMove(Path path, long id) {
    if (matches(path)) {
      return path;
    }
    Path newPath = getPath(id);
    logger.info("Moving tempfile {} to {}", path, newPath);
    if (FileSystem.moveTo(path, newPath, false)) {
      return newPath;
    } else {
      logger.error("Unable to move old temporary file {} to {}", path, newPath);
      return path;
    }
  }

  /**
   * Checks if the given {@code path} matches the current configuration, i.e., it is in the
   * temporary directory and its filename starts with the correct prefix.
   *
   * @param path the path to check
   * @return {@code true} if the path matches the current configuration, {@code false} otherwise
   */
  protected boolean matches(Path path) {
    return FilePath.equals(path.getParent(), tmpDir)
        && path.getFileName().toString().startsWith(prefix);
  }

  /**
   * Initializes the temporary directory. If the specified directory is {@code null}, the default
   * temporary directory is used. The directory is created if it does not exist, and its permissions
   * are checked.
   *
   * @param dir the temporary directory to use, or {@code null} to use the default
   * @return the initialized temporary directory as a {@link Path}
   * @throws IOException if an I/O error occurs, such as if the directory cannot be created or if it
   *     does not have the necessary read/write permissions
   */
  private Path initializeDirectory(Path dir) throws IOException {
    Path resolvedDir =
        (dir == null)
            ? FilePath.getCanonicalFile(Path.of(System.getProperty("java.io.tmpdir")))
            : FilePath.getCanonicalFile(dir);

    Files.createDirectories(resolvedDir);

    if (!Files.isDirectory(resolvedDir)
        || !Files.isReadable(resolvedDir)
        || !Files.isWritable(resolvedDir)) {
      throw new IOException("Not a directory or cannot read/write: " + resolvedDir);
    }

    return resolvedDir;
  }

  /**
   * Wipes all existing files in the temporary directory that match the current prefix. Logs
   * statistics about the number of files wiped and the time taken.
   *
   * @throws IOException if an I/O error occurs while listing or deleting files
   */
  private void wipeExistingFiles() throws IOException {
    long startWipe = System.currentTimeMillis();
    var stats = new WipeStats();

    try (Stream<Path> files = Files.list(tmpDir)) {
      files.forEach(path -> processFile(path, stats));
    }

    long endWipe = System.currentTimeMillis();
    logger
        .atInfo()
        .setMessage(
            "Deleted {} of {} temporary files ({} non-temp files in temp directory) in" + " {}")
        .addArgument(stats.wipedFiles)
        .addArgument(stats.wipeableFiles)
        .addArgument(stats.count - stats.wipeableFiles)
        .addArgument(() -> TimeUtil.formatTime(endWipe - startWipe))
        .log();
  }

  /**
   * Processes a single file during the wiping process. Deletes the file if it matches the prefix
   * and updates the provided statistics.
   *
   * @param path the path of the file to process
   * @param stats the statistics to update
   */
  private void processFile(Path path, WipeStats stats) {
    if (stats.count % 1000 == 0 && stats.count > 0) {
      // User may want some feedback during startup
      logger.info(
          "Deleted {} temp files ({} non-temp files in temp dir)",
          stats.wipedFiles,
          stats.count - stats.wipeableFiles);
    }

    stats.incrementCount();

    String filename = path.getFileName().toString();
    boolean matchesPrefix =
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? filename.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))
            : filename.startsWith(prefix);

    if (matchesPrefix) {
      stats.incrementWipeable();
      try {
        Files.deleteIfExists(path);
        stats.incrementWiped();
      } catch (IOException e) {
        logger.error("Unable to delete temporary file {} - permissions problem?", path);
      }
    }
  }

  /** Helper class to keep track of file wiping statistics. */
  private static class WipeStats {
    /** Constructs a new {@code WipeStats} with zero wiped and wipeable file counts. */
    WipeStats() {
      this(0, 0);
    }

    /**
     * Constructs a new {@code WipeStats} with the specified wiped and wipeable file counts.
     *
     * @param wipedFiles the initial number of wiped files
     * @param wipeableFiles the initial number of wipeable files
     */
    WipeStats(long wipedFiles, long wipeableFiles) {
      this.wipedFiles = wipedFiles;
      this.wipeableFiles = wipeableFiles;
    }

    /** Increments the count of wipeable files. */
    void incrementWipeable() {
      wipeableFiles++;
    }

    /** Increments the count of wiped files. */
    void incrementWiped() {
      wipedFiles++;
    }

    /** Increments the total file count. */
    void incrementCount() {
      count++;
    }

    /** The number of files that have been successfully wiped. */
    long wipedFiles;

    /** The number of files that are considered wipeable (i.e., match the prefix). */
    long wipeableFiles;

    /** The total number of files encountered. */
    long count = 0;
  }

  /** The random number generator used for generating filenames. */
  private final Random random;

  /** The prefix used for generated filenames. */
  private final String prefix;

  /** The temporary directory used by this {@code FilenameGenerator}. */
  private final Path tmpDir;
}
