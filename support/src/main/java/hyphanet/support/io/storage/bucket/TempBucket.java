package hyphanet.support.io.storage.bucket;

import static hyphanet.support.io.storage.TempStorageManager.TRACE_STORAGE_LEAKS;

import hyphanet.base.SizeUtil;
import hyphanet.support.GlobalCleaner;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.TempStorage;
import hyphanet.support.io.storage.TempStorageTracker;
import hyphanet.support.io.storage.rab.RabFactory;
import hyphanet.support.io.storage.rab.TempRab;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;
import java.io.*;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempBucket extends AbstractStorage implements TempStorage, RandomAccessBucket {
  private static final Logger logger = LoggerFactory.getLogger(TempBucket.class);

  public TempBucket(
      TempStorageTracker ramTracker,
      long now,
      RandomAccessBucket cur,
      Path tempFileDir,
      long maxRamSize,
      long ramStoragePoolSize,
      long minDiskSpace,
      BucketFactory fileBucketFactory,
      RabFactory rabMigrateToFactory) {
    this.ramTracker = ramTracker;

    if (cur == null) {
      throw new NullPointerException();
    }
    this.underlyingBucket = cur;
    this.creationTime = now;

    this.tempFileDir = tempFileDir;
    this.maxRamSize = maxRamSize;
    this.ramStoragePoolSize = ramStoragePoolSize;
    this.minDiskSpace = minDiskSpace;

    this.fileBucketFactory = fileBucketFactory;
    this.rabMigrateToFactory = rabMigrateToFactory;

    this.osIndex = 0;
    this.tbis = new ArrayList<>(1);
    logger.info("Created {}", this);

    ramTracker.addToQueue(this);

    registerCleaner();
  }

  /** A blocking method to force-migrate from a RAMBucket to a FileBucket */
  @Override
  public final boolean migrateToDisk() throws IOException {
    Bucket toMigrate;
    long size;
    synchronized (this) {
      if (!isRamStorage() || disposed()) {
        // Nothing to migrate! We don't want to switch back to ram, do we?
        return false;
      }
      toMigrate = underlyingBucket;
      RandomAccessBucket tempFB = fileBucketFactory.makeBucket(underlyingBucket.size());
      size = currentSize;
      if (os != null) {
        os.flush();
        os.close();
        // DO NOT INCREMENT THE osIndex HERE!
        os = tempFB.getOutputStreamUnbuffered();
        if (size > 0) {
          BucketTools.copyTo(toMigrate, os, size);
        }
      } else {
        if (size > 0) {
          try (OutputStream temp = tempFB.getOutputStreamUnbuffered()) {
            BucketTools.copyTo(toMigrate, temp, size);
          }
        }
      }
      if (toMigrate.isReadOnly()) {
        tempFB.setReadOnly();
      }

      closeInputStreams(false);

      ramTracker.removeFromQueue(this);

      underlyingBucket = tempFB;
      // We need streams to be reset to point to the new bucket

      registerCleaner();
    }
    logger.info("We have migrated {}", toMigrate.hashCode());

    // We can free it on-thread as it's a rambucket
    toMigrate.dispose();
    // Might have changed already so we can't rely on currentSize!
    return true;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return new BufferedOutputStream(getOutputStreamUnbuffered());
  }

  @Override
  public synchronized OutputStream getOutputStreamUnbuffered() throws IOException {
    if (os != null) {
      throw new IOException("Only one OutputStream per bucket on " + this + " !");
    }
    if (disposed()) {
      throw new IOException("Already freed");
    }
    // Hence we don't need to reset currentSize / _hasTaken() if a bucket is reused.
    // FIXME we should migrate to disk rather than throwing.
    hasWritten = true;
    ++osIndex;
    OutputStream tos = new TempBucketOutputStream();
    logger.info("Got {} for {}", tos, this);
    return tos;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(getInputStreamUnbuffered());
  }

  @Override
  public synchronized InputStream getInputStreamUnbuffered() throws IOException {
    if (!hasWritten) {
      throw new IOException(
          "No OutputStream has been openned! Why would you want an InputStream " + "then?");
    }
    if (disposed()) {
      throw new IOException("Already disposed");
    }
    TempBucketInputStream is = new TempBucketInputStream(osIndex);
    tbis.add(is);
    logger.info("Got {} for {}", is, this);
    return is;
  }

  @Override
  public synchronized String getName() {
    return underlyingBucket.getName();
  }

  @Override
  public synchronized long size() {
    return currentSize;
  }

  @Override
  public synchronized boolean isReadOnly() {
    return underlyingBucket.isReadOnly();
  }

  @Override
  public synchronized void setReadOnly() {
    underlyingBucket.setReadOnly();
  }

  @Override
  public synchronized void dispose() {
    if (!setDisposed()) {
      return;
    }

    close();

    GlobalCleaner.getInstance().clean(cleanable);
  }

  @Override
  public void close() {
    if (!setClosed()) {
      return;
    }
    underlyingBucket.close();
    ramTracker.removeFromQueue(this);
  }

  @Override
  public RandomAccessBucket createShadow() {
    return underlyingBucket.createShadow();
  }

  @Override
  public long creationTime() {
    return creationTime;
  }

  @Override
  public void onResume(ResumeContext context) {
    // Not persistent.
    throw new IllegalStateException();
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    throw new UnsupportedOperationException(); // Not persistent.
  }

  @Override
  public TempRab toRandomAccessBuffer() throws IOException {
    synchronized (this) {
      if (disposed()) {
        throw new IOException("Already disposed");
      }
      if (os != null) {
        throw new IOException("Can't migrate with open OutputStream's");
      }
      if (!tbis.isEmpty()) {
        throw new IOException("Can't migrate with open InputStream's");
      }
      setReadOnly();
      var underlyingRab = underlyingBucket.toRandomAccessBuffer();
      TempRab rab = new TempRab(ramTracker, underlyingRab, creationTime, rabMigrateToFactory, this);
      ramTracker.removeFromQueue(this);

      underlyingBucket = new RabBucket(rab);
      // Register a cleanup action to dispose the new underlying bucket when disposing this bucket.
      registerCleaner();

      return rab;
    }
  }

  /** Only for testing */
  public synchronized Bucket getUnderlying() {
    return underlyingBucket;
  }

  private void registerCleaner() {
    cleanable =
        GlobalCleaner.getInstance()
            .register(this, new CleaningAction(this.underlyingBucket, tbis, os));
  }

  private synchronized void closeInputStreams(boolean forFree) {
    for (ListIterator<TempBucketInputStream> i = tbis.listIterator(); i.hasNext(); ) {
      TempBucketInputStream is = i.next();
      if (forFree) {
        i.remove();
        try {
          is.close();
        } catch (IOException e) {
          logger.error("Caught {} closing {}", e, is);
        }
      } else {
        try {
          is.maybeResetInputStream();
        } catch (IOException e) {
          i.remove();
          try {
            is.close();
          } catch (IOException e2) {
            logger.error("Caught {} closing {}", e2, is);
          }
        }
      }
    }
  }

  static class CleaningAction implements Runnable {
    CleaningAction(
        Bucket currentBucket,
        List<TempBucketInputStream> inputStreams,
        @Nullable OutputStream outputStream) {
      this.currentBucket = currentBucket;
      this.inputStreams = new ArrayList<>(inputStreams);
      this.outputStream = outputStream;
    }

    @Override
    public void run() {
      if (currentBucket == null) {
        // Already disposed
        return;
      }

      var size = currentBucket.size();

      String msg = "TempBucket not freed, size={} : {}";
      if (TRACE_STORAGE_LEAKS) {
        logger.error(msg, size, this, new Throwable());
      } else {
        logger.error(msg, size, this);
      }

      // Close input and output streams
      for (TempBucketInputStream is : inputStreams) {
        try {
          is.close();
        } catch (IOException e) {
          logger.warn("Error closing input stream: {}", is, e);
        }
      }

      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (IOException e) {
          logger.warn("Error closing output stream", e);
        }
      }

      currentBucket.dispose();
    }

    private final Bucket currentBucket;
    private final List<TempBucketInputStream> inputStreams;
    private final @Nullable OutputStream outputStream;
  }

  private class TempBucketOutputStream extends OutputStream {
    static final long CHECK_DISK_EVERY = 4096;

    TempBucketOutputStream() throws IOException {
      if (os == null) {
        os = underlyingBucket.getOutputStreamUnbuffered();
      }
      underlyingOs = os;
    }

    @Override
    public final void write(int b) throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        long futureSize = currentSize + 1;
        maybeMigrateRamBucket(futureSize);
        underlyingOs.write(b);
        currentSize = futureSize;
      }
    }

    @Override
    public final void write(byte[] b, int off, int len) throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        long futureSize = currentSize + len;
        maybeMigrateRamBucket(futureSize);
        underlyingOs.write(b, off, len);
        currentSize = futureSize;
      }
    }

    @Override
    public final void flush() throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          return;
        }
        maybeMigrateRamBucket(currentSize);
        if (!closed) {
          underlyingOs.flush();
        }
      }
    }

    @Override
    public final void close() throws IOException {
      synchronized (TempBucket.this) {
        if (closed) {
          return;
        }
        maybeMigrateRamBucket(currentSize);
        underlyingOs.flush();
        underlyingOs.close();
        os = null;
        closed = true;
      }
    }

    private void maybeMigrateRamBucket(long futureSize) throws IOException {
      if (closed) {
        return;
      }
      if (isRamStorage()) {
        boolean shouldMigrate = false;
        boolean isOversized = false;

        if (futureSize >= Math.min(Integer.MAX_VALUE, maxRamSize)) {
          isOversized = true;
          shouldMigrate = true;
        } else if ((futureSize - currentSize) + ramTracker.getRamBytesInUse()
            >= ramStoragePoolSize) {
          shouldMigrate = true;
        }

        if (shouldMigrate) {
          if (isOversized) {
            logger
                .atInfo()
                .setMessage("The bucket {} is over {}: we will " + "force-migrate it to disk.")
                .addArgument(TempBucket.this)
                .addArgument(() -> SizeUtil.formatSize(maxRamSize))
                .log();
          } else {
            logger.info("The bucketpool is full: force-migrate before " + "we go over the limit");
          }
          migrateToDisk();
          underlyingOs = os;
        }
      } else {
        // Check for excess disk usage.
        if (futureSize - lastCheckedSize >= CHECK_DISK_EVERY) {
          if (Files.getFileStore(tempFileDir).getUsableSpace() - (futureSize - currentSize)
              < minDiskSpace) {
            throw new InsufficientDiskSpaceException();
          }
          lastCheckedSize = futureSize;
        }
      }
    }

    long lastCheckedSize = 0;
    boolean closed = false;
    OutputStream underlyingOs;
  }

  private class TempBucketInputStream extends InputStream {
    TempBucketInputStream(short idx) throws IOException {
      this.idx = idx;
      this.currentIS = underlyingBucket.getInputStreamUnbuffered();
    }

    public void maybeResetInputStream() throws IOException {
      if (idx != osIndex) {
        close();
      } else {
        try {
          currentIS.close();
        } catch (IOException e) {
          logger.warn("Failed to close input stream", e);
        }
        currentIS = underlyingBucket.getInputStreamUnbuffered();
        long toSkip = index;
        while (toSkip > 0) {
          toSkip -= currentIS.skip(toSkip);
        }
      }
    }

    @Override
    public final int read() throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        int toReturn = currentIS.read();
        if (toReturn != -1) {
          index++;
        }
        return toReturn;
      }
    }

    @Override
    public int read(byte[] b) throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        return read(b, 0, b.length);
      }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        int toReturn = currentIS.read(b, off, len);
        if (toReturn > 0) {
          index += toReturn;
        }
        return toReturn;
      }
    }

    @Override
    public long skip(long n) throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        long skipped = currentIS.skip(n);
        index += skipped;
        return skipped;
      }
    }

    @Override
    public int available() throws IOException {
      synchronized (TempBucket.this) {
        if (disposed()) {
          throw new IOException("Already disposed");
        }
        return currentIS.available();
      }
    }

    @Override
    public final void close() throws IOException {
      synchronized (TempBucket.this) {
        currentIS.close();
        tbis.remove(this);
      }
    }

    /** Will change if a new OutputStream is openned: used to detect deprecation */
    private final short idx;

    /** The current InputStream we use from the underlying bucket */
    private InputStream currentIS;

    /** Keep a counter to know where we are on the stream (useful when we have to reset and skip) */
    private long index = 0;
  }

  /** A timestamp used to evaluate the age of the bucket and maybe consider it for a migration */
  private final long creationTime;

  private final TempStorageTracker ramTracker;

  /** All the open-streams to reset or close on migration or free() */
  private final ArrayList<TempBucketInputStream> tbis;

  /**
   * The maximum RAM size allowed for this bucket. If over this size, the bucket will be migrated to
   * a file bucket.
   */
  private final long maxRamSize;

  private final long ramStoragePoolSize;
  private final Path tempFileDir;
  private final long minDiskSpace;

  private final BucketFactory fileBucketFactory;
  private final RabFactory rabMigrateToFactory;
  private Cleaner.Cleanable cleanable;

  /** The underlying bucket itself */
  private RandomAccessBucket underlyingBucket;

  /**
   * We have to account the size of the underlying bucket ourselves in order to be able to access it
   * fast
   */
  private long currentSize;

  /** Has an OutputStream been opened at some point? */
  private boolean hasWritten;

  /** A link to the "real" underlying outputStream, even if we migrated */
  private @Nullable OutputStream os;

  /** An identifier used to know when to deprecate the InputStreams */
  private short osIndex;
}
