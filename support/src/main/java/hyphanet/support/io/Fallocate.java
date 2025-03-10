package hyphanet.support.io;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
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

  private static final boolean IS_LINUX = Platform.isLinux();
  private static final boolean IS_POSIX =
      !Platform.isWindows() && !Platform.isMac() && !Platform.isOpenBSD();
  private static final boolean IS_ANDROID = Platform.isAndroid();

  private static final int FALLOC_FL_KEEP_SIZE = 0x01;

  private Fallocate(FileChannel channel, int fd, long finalFileSize) {
    this.fd = fd;
    this.finalFileSize = finalFileSize;
    this.channel = channel;
  }

  public static Fallocate forChannel(FileChannel channel, long finalFileSize) {
    return new Fallocate(channel, getDescriptor(channel), finalFileSize);
  }

  public static Fallocate forChannel(FileChannel channel, FileDescriptor fd, long finalFileSize) {
    return new Fallocate(channel, getDescriptor(fd), finalFileSize);
  }

  public Fallocate fromOffset(long offset) {
    if (offset < 0 || offset > finalFileSize) {
      throw new IllegalArgumentException();
    }
    this.offset = offset;
    return this;
  }

  public Fallocate keepSize() {
    requireLinux("fallocate keep size");
    mode |= FALLOC_FL_KEEP_SIZE;
    return this;
  }

  public void execute() throws IOException {
    int errno = 0;
    boolean isUnsupported = false;
    if (IS_LINUX) {
      final int result = FallocateHolder.fallocate(fd, mode, offset, finalFileSize - offset);
      errno = result == 0 ? 0 : Native.getLastError();
    } else if (IS_POSIX) {
      errno = FallocateHolderPOSIX.posix_fallocate(fd, offset, finalFileSize - offset);
    } else {
      isUnsupported = true;
    }

    if (isUnsupported || errno != 0) {
      logger.info("fallocate() failed; using legacy method; errno={}", errno);
      legacyFill(channel, finalFileSize, offset);
    }
  }

  private void requireLinux(String feature) {
    if (!IS_LINUX) {
      throwUnsupported(feature);
    }
  }

  private void throwUnsupported(String feature) {
    throw new UnsupportedOperationException(feature + " is not supported on this file system");
  }

  private static int getDescriptor(FileChannel channel) {
    try {
      // sun.nio.ch.FileChannelImpl declares private final java.io.FileDescriptor fd
      final Field field = channel.getClass().getDeclaredField("fd");
      field.setAccessible(true);
      return getDescriptor((FileDescriptor) field.get(channel));
    } catch (final Exception e) {
      throw new UnsupportedOperationException("unsupported FileChannel implementation", e);
    }
  }

  private static int getDescriptor(FileDescriptor descriptor) {
    try {
      // Oracle java.io.FileDescriptor declares private int fd
      final Field field = descriptor.getClass().getDeclaredField(IS_ANDROID ? "descriptor" : "fd");
      field.setAccessible(true);
      return (int) field.get(descriptor);
    } catch (final Exception e) {
      throw new UnsupportedOperationException("unsupported FileDescriptor implementation", e);
    }
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

  private static class FallocateHolder {
    static {
      Native.register(FallocateHolder.class, Platform.C_LIBRARY_NAME);
    }

    private static native int fallocate(int fd, int mode, long offset, long length);
  }

  private static class FallocateHolderPOSIX {
    static {
      Native.register(FallocateHolderPOSIX.class, Platform.C_LIBRARY_NAME);
    }

    @SuppressWarnings("java:S100")
    private static native int posix_fallocate(int fd, long offset, long length);
  }

  private final int fd;
  private final long finalFileSize;
  private final FileChannel channel;
  private int mode;
  private long offset;
}
