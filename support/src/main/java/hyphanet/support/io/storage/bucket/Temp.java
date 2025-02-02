package hyphanet.support.io.storage.bucket;

import hyphanet.base.SizeUtil;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.ToDiskMigratable;
import hyphanet.support.io.storage.randomaccessbuffer.RandomAccessBuffer;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ListIterator;

import static hyphanet.support.io.storage.bucket.TempBucketFactory.TRACE_BUCKET_LEAKS;

public class Temp implements Bucket, ToDiskMigratable, RandomAccessible {
  /** A timestamp used to evaluate the age of the bucket and maybe consider it for a migration */
  public final long creationTime;

  private static final Logger logger = LoggerFactory.getLogger(Temp.class);

  public Temp(TempBucketFactory factory, long now, RandomAccessible cur) {
    this.factory = factory;
    if (cur == null) {
      throw new NullPointerException();
    }
    if (TRACE_BUCKET_LEAKS) {
      tracer = new Throwable();
    } else {
      tracer = null;
    }
    this.currentBucket = cur;
    this.creationTime = now;
    this.osIndex = 0;
    this.tbis = new ArrayList<>(1);
    logger.info("Created {}", this);
  }

  /** A blocking method to force-migrate from a RAMBucket to a FileBucket */
  public final boolean migrateToDisk() throws IOException {
    Bucket toMigrate = null;
    long size;
    synchronized (this) {
      if (!isRAMBucket() || hasBeenDisposed)
      // Nothing to migrate! We don't want to switch back to ram, do we?

      {
        return false;
      }
      toMigrate = currentBucket;
      RandomAccessible tempFB = factory._makeFileBucket();
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
          OutputStream temp = tempFB.getOutputStreamUnbuffered();
          try {
            BucketTools.copyTo(toMigrate, temp, size);
          } finally {
            temp.close();
          }
        }
      }
      if (toMigrate.isReadOnly()) {
        tempFB.setReadOnly();
      }

      closeInputStreams(false);

      currentBucket = tempFB;
      // We need streams to be reset to point to the new bucket
    }
    logger.info("We have migrated {}", toMigrate.hashCode());

    synchronized (factory.ramBucketQueue) {
      factory.ramBucketQueue.remove(getReference());
    }

    // We can free it on-thread as it's a rambucket
    toMigrate.dispose();
    // Might have changed already so we can't rely on currentSize!
    factory._hasDisposed(size);
    return true;
  }

  public final synchronized boolean isRAMBucket() {
    return (currentBucket instanceof Array);
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
    if (hasBeenDisposed) {
      throw new IOException("Already freed");
    }
    // Hence we don't need to reset currentSize / _hasTaken() if a bucket is reused.
    // FIXME we should migrate to disk rather than throwing.
    hasWritten = true;
    OutputStream tos = new TempBucketOutputStream(++osIndex);
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
    if (hasBeenDisposed) {
      throw new IOException("Already freed");
    }
    TempBucketInputStream is = new TempBucketInputStream(osIndex);
    tbis.add(is);
    logger.info("Got {} for {}", is, this);
    return is;
  }

  @Override
  public synchronized String getName() {
    return currentBucket.getName();
  }

  @Override
  public synchronized long size() {
    return currentSize;
  }

  @Override
  public synchronized boolean isReadOnly() {
    return currentBucket.isReadOnly();
  }

  @Override
  public synchronized void setReadOnly() {
    currentBucket.setReadOnly();
  }

  @Override
  public synchronized void dispose() {
    Bucket cur;
    synchronized (this) {
      if (hasBeenDisposed) {
        return;
      }
      hasBeenDisposed = true;

      try {
        os.close();
      } catch (IOException e) {
        logger.warn("Error closing output stream", e);
      }
      closeInputStreams(true);
      if (isRAMBucket()) {
        // If it's in memory we must free before removing from the queue.
        currentBucket.dispose();
        factory._hasDisposed(currentSize);
        synchronized (factory.ramBucketQueue) {
          factory.ramBucketQueue.remove(getReference());
        }
        return;
      } else {
        // Better to free outside the lock if it's not in-memory.
        cur = currentBucket;
      }
    }
    cur.dispose();
  }

  @Override
  public void close() {
    currentBucket.close();
  }

  @Override
  public RandomAccessible createShadow() {
    return currentBucket.createShadow();
  }

  public WeakReference<ToDiskMigratable> getReference() {
    return weakRef;
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
  public RandomAccessBuffer toRandomAccessBuffer() throws IOException {
    synchronized (this) {
      if (hasBeenDisposed) {
        throw new IOException("Already freed");
      }
      if (os != null) {
        throw new IOException("Can't migrate with open OutputStream's");
      }
      if (!tbis.isEmpty()) {
        throw new IOException("Can't migrate with open InputStream's");
      }
      setReadOnly();
      hyphanet.support.io.storage.randomaccessbuffer.Temp rab =
          new hyphanet.support.io.storage.randomaccessbuffer.Temp(
              factory, currentBucket.toRandomAccessBuffer(), creationTime, !isRAMBucket(), this);
      if (isRAMBucket()) {
        synchronized (factory.ramBucketQueue) {
          // No change in space usage.
          factory.ramBucketQueue.remove(getReference());
          factory.ramBucketQueue.add(rab.getReference());
        }
      }
      currentBucket = new Rab(rab);
      return rab;
    }
  }

  /** Called only by TempRandomAccessBuffer */
  public synchronized void onDisposed() {
    hasBeenDisposed = true;
  }

  /** Only for testing */
  synchronized Bucket getUnderlying() {
    return currentBucket;
  }

  @Override
  protected void finalize() throws Throwable {
    // If it's been converted to a TempRandomAccessBuffer, finalize() will only be
    // called
    // if *neither* object is reachable.
    if (!hasBeenDisposed) {
      if (TRACE_BUCKET_LEAKS) {
        logger.error(
            "TempBucket not freed, size={}, isRAMBucket={} : {}",
            size(),
            isRAMBucket(),
            this,
            tracer);
      } else {
        logger.error(
            "TempBucket not freed, size={}, isRAMBucket={} : {}", size(), isRAMBucket(), this);
      }
      dispose();
    }
    super.finalize();
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
          is._maybeResetInputStream();
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

  private class TempBucketOutputStream extends OutputStream {
    TempBucketOutputStream(short idx) throws IOException {
      if (os == null) {
        os = currentBucket.getOutputStreamUnbuffered();
      }
    }

    @Override
    public final void write(int b) throws IOException {
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already freed");
        }
        long futureSize = currentSize + 1;
        _maybeMigrateRamBucket(futureSize);
        os.write(b);
        currentSize = futureSize;
        if (isRAMBucket()) // We need to re-check because it might have changed!
        {
          factory._hasTaken(1);
        }
      }
    }

    @Override
    public final void write(byte[] b, int off, int len) throws IOException {
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already freed");
        }
        long futureSize = currentSize + len;
        _maybeMigrateRamBucket(futureSize);
        os.write(b, off, len);
        currentSize = futureSize;
        if (isRAMBucket()) // We need to re-check because it might have changed!
        {
          factory._hasTaken(len);
        }
      }
    }

    @Override
    public final void flush() throws IOException {
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          return;
        }
        _maybeMigrateRamBucket(currentSize);
        if (!closed) {
          os.flush();
        }
      }
    }

    @Override
    public final void close() throws IOException {
      synchronized (Temp.this) {
        if (closed) {
          return;
        }
        _maybeMigrateRamBucket(currentSize);
        os.flush();
        os.close();
        os = null;
        closed = true;
      }
    }

    private void _maybeMigrateRamBucket(long futureSize) throws IOException {
      if (closed) {
        return;
      }
      if (isRAMBucket()) {
        boolean shouldMigrate = false;
        boolean isOversized = false;

        if (futureSize
            >= Math.min(
                Integer.MAX_VALUE,
                factory.getMaxRamBucketSize() * TempBucketFactory.RAMBUCKET_CONVERSION_FACTOR)) {
          isOversized = true;
          shouldMigrate = true;
        } else if ((futureSize - currentSize) + factory.getRamUsed() >= factory.getMaxRamUsed()) {
          shouldMigrate = true;
        }

        if (shouldMigrate) {
          if (isOversized) {
            logger
                .atInfo()
                .setMessage("The bucket {} is over {}: we will " + "force-migrate it to disk.")
                .addArgument(Temp.this)
                .addArgument(
                    () ->
                        SizeUtil.formatSize(
                            factory.getMaxRamBucketSize()
                                * TempBucketFactory.RAMBUCKET_CONVERSION_FACTOR))
                .log();
          } else {
            logger.info("The bucketpool is full: force-migrate before " + "we go over the limit");
          }
          migrateToDisk();
        }
      } else {
        // Check for excess disk usage.
        if (futureSize - lastCheckedSize >= CHECK_DISK_EVERY) {
          if (Files.getFileStore(factory.getFilenameGenerator().getDir()).getUsableSpace()
                  + (futureSize - currentSize)
              < factory.getMinDiskSpace()) {
            throw new InsufficientDiskSpaceException();
          }
          lastCheckedSize = futureSize;
        }
      }
    }

    long lastCheckedSize = 0;
    long CHECK_DISK_EVERY = 4096;
    boolean closed = false;
  }

  private class TempBucketInputStream extends InputStream {
    TempBucketInputStream(short idx) throws IOException {
      this.idx = idx;
      this.currentIS = currentBucket.getInputStreamUnbuffered();
    }

    public void _maybeResetInputStream() throws IOException {
      if (idx != osIndex) {
        close();
      } else {
        try {
          currentIS.close();
        } catch (IOException e) {
          logger.warn("Failed to close input stream", e);
        }
        currentIS = currentBucket.getInputStreamUnbuffered();
        long toSkip = index;
        while (toSkip > 0) {
          toSkip -= currentIS.skip(toSkip);
        }
      }
    }

    @Override
    public final int read() throws IOException {
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already freed");
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
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already freed");
        }
        return read(b, 0, b.length);
      }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already freed");
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
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already disposed");
        }
        long skipped = currentIS.skip(n);
        index += skipped;
        return skipped;
      }
    }

    @Override
    public int available() throws IOException {
      synchronized (Temp.this) {
        if (hasBeenDisposed) {
          throw new IOException("Already disposed");
        }
        return currentIS.available();
      }
    }

    @Override
    public boolean markSupported() {
      return false;
    }

    @Override
    public final void close() throws IOException {
      synchronized (Temp.this) {
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

  private final TempBucketFactory factory;

  /** All the open-streams to reset or close on migration or free() */
  private final ArrayList<TempBucketInputStream> tbis;

  private final Throwable tracer;
  private final WeakReference<ToDiskMigratable> weakRef = new WeakReference<>(this);

  /** The underlying bucket itself */
  private RandomAccessible currentBucket;

  /**
   * We have to account the size of the underlying bucket ourself in order to be able to access it
   * fast
   */
  private long currentSize;

  /** Has an OutputStream been opened at some point? */
  private boolean hasWritten;

  /** A link to the "real" underlying outputStream, even if we migrated */
  private OutputStream os = null;

  /** An identifier used to know when to deprecate the InputStreams */
  private short osIndex;

  private boolean hasBeenDisposed = false;
}
