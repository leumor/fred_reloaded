package hyphanet.support.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides access to operating system-specific {@code fallocate} and {@code posix_fallocate}
 * functions.
 * https://stackoverflow.com/questions/18031841/pre-allocating-drive-space-for-file-storage
 */
// TODO: Reimplement this class using the Foreign Function and Memory API (FFM) and without
// reflection.
public final class Fallocate {
  private static final Logger logger = LoggerFactory.getLogger(Fallocate.class);

  public Fallocate(FileChannel channel, long finalFileSize) {
    this.finalFileSize = finalFileSize;
    this.channel = channel;
  }

  public Fallocate fromOffset(long offset) {
    if (offset < 0 || offset > finalFileSize) {
      throw new IllegalArgumentException();
    }
    this.offset = offset;
    return this;
  }

  public void execute() throws IOException {
    logger.info("fallocate using legacy method");
    legacyFill(channel, finalFileSize, offset);
  }

  private static void legacyFill(FileChannel fc, long newLength, long offset) throws IOException {
    var rng = RandomGenerator.getDefault();
    byte[] b = new byte[4096];
    ByteBuffer bb = ByteBuffer.wrap(b);
    while (offset < newLength) {
      bb.rewind();
      rng.nextBytes(b);
      offset += fc.write(bb, offset);
      if (offset % (1024 * 1024 * 1024L) == 0) {
        rng = RandomGenerator.getDefault();
      }
    }
  }

  private final long finalFileSize;
  private final FileChannel channel;
  private long offset;
}
