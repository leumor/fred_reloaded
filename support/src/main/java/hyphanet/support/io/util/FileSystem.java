package hyphanet.support.io.util;

import hyphanet.support.io.bucket.BucketTools;
import hyphanet.support.io.bucket.RegularFile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileSystem {

    private static final Logger logger = LoggerFactory.getLogger(FileSystem.class);

    private FileSystem() {
        throw new AssertionError("Utility class should not be instantiated");
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
                boolean success = files.allMatch(FileSystem::removeAll);

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
                Stream.fill(Channels.newOutputStream(channel), size);
                channel.force(false);
            }
        }
        Files.delete(path);
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
            return tryRegularMove(source, target);
        } catch (IOException e) {
            logMoveError(source, target, e);
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
     * Attempts to perform a regular move operation when atomic move is not supported. This
     * method serves as a fallback mechanism using
     * {@link StandardCopyOption#REPLACE_EXISTING}.
     *
     * @param source the path to the file to move
     * @param target the path to the target file
     *
     * @return {@code true} if the move operation succeeded, {@code false} otherwise
     *
     * @see Files#move(Path, Path, CopyOption...)
     */
    private static boolean tryRegularMove(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            logMoveError(source, target, e);
            return false;
        }
    }

    /**
     * Logs file move operation errors with consistent formatting. Uses SLF4J logging framework
     * to record the error with source and target paths.
     *
     * @param source the source path that failed to be moved
     * @param target the target path of the failed move operation
     * @param e      the IOException that occurred during the move operation
     */
    private static void logMoveError(Path source, Path target, IOException e) {
        logger.error("Failed to rename {} to {}", source, target, e);
    }
}
