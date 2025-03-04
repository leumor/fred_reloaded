package hyphanet.support.io;

import java.nio.file.Path;

/**
 * Interface for checking available disk space before writing to files.
 *
 * <p>This interface provides functionality to verify if there is sufficient disk space available
 * before performing write operations on files. It helps prevent disk space exhaustion and potential
 * system failures.
 */
public interface DiskSpaceChecker {

  /**
   * Verifies if there is enough disk space available to extend a file.
   *
   * @param path The path of the file that needs to be extended
   * @param toWrite The number of bytes to be written
   * @param bufferSize The threshold of accumulated bytes before performing a disk space check. The
   *     caller only checks disk space when the number of bytes written since the last check exceeds
   *     this value.
   * @return {@code true} if there is sufficient disk space available, {@code false} otherwise
   */
  boolean checkDiskSpace(Path path, int toWrite, int bufferSize);
}
