package hyphanet.support.io;

import hyphanet.base.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Tracks the current temporary files settings (dir and prefix), and translates between ID's
 * and filenames. Also provides functions for creating tempfiles (which should be safe against
 * symlink attacks and race conditions). FIXME Consider using File.createTempFile(). Note that
 * using our own code could actually be more secure if we use a better PRNG than they do (they
 * use "new Random()" IIRC, but maybe that's fixed now?). If we do change to using
 * File.createTempFile(), we will need to change TempFileBucket accordingly.
 *
 * @author toad
 */
public class FilenameGenerator {

    private static final Logger logger = LoggerFactory.getLogger(FilenameGenerator.class);

    /**
     * @param random
     * @param wipeFiles
     * @param dir       if <code>null</code> then use the default temporary directory
     * @param prefix
     *
     * @throws IOException
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

    public Path getPath(long id) {
        return tmpDir.resolve(prefix + Long.toHexString(id));
    }

    public Path makeRandomFile() throws IOException {
        return getPath(makeRandomFilename());
    }

    public Path getDir() {
        return tmpDir;
    }

    public Path maybeMove(Path path, long id) {
        if (matches(path)) {
            return path;
        }
        Path newPath = getPath(id);
        logger.info("Moving tempfile {} to {}", path, newPath);
        if (FileIoUtil.moveTo(path, newPath, false)) {
            return newPath;
        } else {
            logger.error("Unable to move old temporary file {} to {}", path, newPath);
            return path;
        }
    }

    protected boolean matches(Path path) {
        return FileIoUtil.equals(path.getParent(), tmpDir) &&
               path.getFileName().toString().startsWith(prefix);
    }

    private Path initializeDirectory(Path dir) throws IOException {
        Path resolvedDir =
            (dir == null) ? FileUtil.getCanonicalFile(Path.of(System.getProperty(
                "java.io.tmpdir"))) : FileUtil.getCanonicalFile(dir);

        Files.createDirectories(resolvedDir);

        if (!Files.isDirectory(resolvedDir) || !Files.isReadable(resolvedDir) ||
            !Files.isWritable(resolvedDir)) {
            throw new IOException("Not a directory or cannot read/write: " + resolvedDir);
        }

        return resolvedDir;
    }

    private void wipeExistingFiles() throws IOException {
        long startWipe = System.currentTimeMillis();
        var stats = new WipeStats();

        try (Stream<Path> files = Files.list(tmpDir)) {
            files.forEach(path -> processFile(path, stats));
        }

        long endWipe = System.currentTimeMillis();
        logger.atInfo().setMessage(
            "Deleted {} of {} temporary files ({} non-temp files in temp directory) in" +
            " {}").addArgument(stats.wipedFiles).addArgument(stats.wipeableFiles).addArgument(
            stats.count - stats.wipeableFiles).addArgument(() -> TimeUtil.formatTime(
            endWipe - startWipe)).log();

    }

    private void processFile(Path path, WipeStats stats) {
        if (stats.count % 1000 == 0 && stats.count > 0) {
            // User may want some feedback during startup
            logger.info(
                "Deleted {} temp files ({} non-temp files in temp dir)",
                stats.wipedFiles,
                stats.count - stats.wipeableFiles
            );
        }

        stats.incrementCount();

        String filename = path.getFileName().toString();
        boolean matchesPrefix = System.getProperty("os.name").toLowerCase().contains("win") ?
            filename.toLowerCase().startsWith(prefix.toLowerCase()) : filename.startsWith(
            prefix);

        if (matchesPrefix) {
            stats.incrementWipeable();
            try {
                Files.deleteIfExists(path);
                stats.incrementWiped();
            } catch (IOException e) {
                logger.error(
                    "Unable to delete temporary file {} - permissions problem?",
                    path
                );
            }
        }
    }


    private static class WipeStats {
        WipeStats() {
            this(0, 0);
        }

        WipeStats(long wipedFiles, long wipeableFiles) {
            this.wipedFiles = wipedFiles;
            this.wipeableFiles = wipeableFiles;
        }

        void incrementWipeable() {
            wipeableFiles++;
        }

        void incrementWiped() {
            wipedFiles++;
        }

        void incrementCount() {
            count++;
        }

        long wipedFiles;
        long wipeableFiles;
        long count = 0;
    }

    private final Random random;
    private final String prefix;
    private final Path tmpDir;

}
