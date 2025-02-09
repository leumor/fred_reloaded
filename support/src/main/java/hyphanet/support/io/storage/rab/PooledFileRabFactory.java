package hyphanet.support.io.storage.rab;

import hyphanet.support.io.FilenameGenerator;

import java.io.IOException;
import java.nio.file.Files;

/**
 * A factory for creating pooled temporary files.
 *
 * <p>This factory utilizes a {@link FilenameGenerator} to create unique filenames for temporary
 * files. It produces {@link PooledFileRab} instances, which are {@link Rab}
 * implementations backed by files on disk and managed by a file descriptor pool.
 *
 * <p>This factory is designed to be used in environments where a large number of temporary files
 * might be created and accessed concurrently, and where limiting the number of open file
 * descriptors is important for system stability and performance.
 *
 * @see PooledFileRab
 * @see FilenameGenerator
 * @see RabFactory
 */
public class PooledFileRabFactory implements RabFactory {

  /**
   * Constructs a new {@link PooledFileRabFactory} with the specified {@link FilenameGenerator}.
   *
   * @param filenameGenerator The {@link FilenameGenerator} to use for creating temporary filenames.
   *     Must not be {@code null}.
   * @throws NullPointerException if {@code filenameGenerator} is {@code null}.
   */
  public PooledFileRabFactory(FilenameGenerator filenameGenerator) {
    fg = filenameGenerator;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException if an I/O error occurs during file creation or deletion.
   */
  @Override
  public Rab makeRab(long size) throws IOException {
    long id = fg.makeRandomFilename();
    var path = fg.getPath(id);
    Rab ret = null;
    try {
      ret = new PooledFileRab(path, false, size, id, true);
      return ret;
    } finally {
      if (ret == null) {
        Files.delete(path);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException if an I/O error occurs during file creation or deletion.
   */
  @Override
  public Rab makeRab(byte[] initialContents, int offset, int size, boolean readOnly)
      throws IOException {
    long id = fg.makeRandomFilename();
    var path = fg.getPath(id);
    Rab ret = null;
    try {
      ret = new PooledFileRab(path, initialContents, offset, size, id, true, readOnly);
      return ret;
    } finally {
      if (ret == null) {
        Files.delete(path);
      }
    }
  }

  /** The {@link FilenameGenerator} used by this factory to generate unique filenames. */
  private final FilenameGenerator fg;
}
