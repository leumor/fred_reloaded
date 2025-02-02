package hyphanet.support.io.storage.randomaccessbuffer;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.ToDiskMigratable;
import hyphanet.support.io.storage.bucket.TempBucketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import static hyphanet.support.io.storage.bucket.TempBucketFactory.TRACE_BUCKET_LEAKS;

/** Unlike a TempBucket, the size is fixed, so migrate only happens on the migration thread. */
public class Temp extends SwitchableProxy implements ToDiskMigratable {
  private static final Logger logger = LoggerFactory.getLogger(Temp.class);

  public Temp(
      TempBucketFactory factory,
      byte[] initialContents,
      int offset,
      int size,
      long time,
      boolean readOnly)
      throws IOException {
    super(new ByteArray(initialContents, offset, size, readOnly), size);
    this.factory = factory;
    creationTime = time;
    hasMigrated = false;
    original = null;
    if (TRACE_BUCKET_LEAKS) {
      tracer = new Throwable();
    } else {
      tracer = null;
    }
  }

  public Temp(
      TempBucketFactory factory,
      RandomAccessBuffer underlying,
      long creationTime,
      boolean migrated,
      hyphanet.support.io.storage.bucket.Temp tempBucket)
      throws IOException {
    super(underlying, underlying.size());
    this.factory = factory;
    this.creationTime = creationTime;
    this.hasMigrated = hasFreedRAM = migrated;
    this.original = tempBucket;
    if (TRACE_BUCKET_LEAKS) {
      tracer = new Throwable();
    } else {
      tracer = null;
    }
  }

  public Temp(TempBucketFactory factory, int size, long time) throws IOException {
    super(new ByteArray(size), size);
    this.factory = factory;
    creationTime = time;
    hasMigrated = false;
    original = null;
    if (TRACE_BUCKET_LEAKS) {
      tracer = new Throwable();
    } else {
      tracer = null;
    }
  }

  @Override
  public void dispose() {
    if (!super.innerDispose()) {
      return;
    }
    logger.info("Freed {}", this);
    if (original != null) {
      // Tell the TempBucket to prevent log spam. Don't call free().
      original.onDisposed();
    }
  }

  public WeakReference<ToDiskMigratable> getReference() {
    return weakRef;
  }

  @Override
  public long creationTime() {
    return creationTime;
  }

  @Override
  public boolean migrateToDisk() throws IOException {
    synchronized (this) {
      if (hasMigrated) {
        return false;
      }
      hasMigrated = true;
    }
    migrate();
    return true;
  }

  public synchronized boolean hasMigrated() {
    return hasMigrated;
  }

  @Override
  public void onResume(ResumeContext context) {
    // Not persistent.
    throw new UnsupportedOperationException();
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected RandomAccessBuffer innerMigrate(RandomAccessBuffer underlying) throws IOException {
    ByteArray b = (ByteArray) underlying;
    byte[] buf = b.getBuffer();
    return factory.getUnderlyingRabFactory().makeRab(buf, 0, (int) size(), b.isReadOnly());
  }

  @Override
  protected void afterDisposeUnderlying() {
    // Called when the in-RAM storage has been freed.
    synchronized (this) {
      if (hasFreedRAM) {
        return;
      }
      hasFreedRAM = true;
    }
    factory._hasDisposed(size());
    synchronized (factory.ramBucketQueue) {
      factory.ramBucketQueue.remove(getReference());
    }
  }

  @Override
  protected void finalize() throws Throwable {
    if (original != null) {
      return; // TempBucket's responsibility if there was one.
    }
    // If it's been converted to a TempRandomAccessBuffer, finalize() will only be
    // called
    // if *neither* object is reachable.
    if (!hasBeenDisposed()) {
      if (TRACE_BUCKET_LEAKS) {
        logger.error("TempRandomAccessBuffer not freed, size={} : {}", size(), this, tracer);
      } else {
        logger.error("TempRandomAccessBuffer not freed, size={} : {}", size(), this);
      }
      dispose();
    }
    super.finalize();
  }

  private final TempBucketFactory factory;
  private final long creationTime;

  /**
   * Kept in RAM so that finalizer is called on the TempBucket when *both* the
   * TempRandomAccessBuffer *and* the TempBucket are no longer reachable, in which case we will free
   * from the TempBucket. If this is null, then the TempRAB can free in finalizer.
   */
  private final hyphanet.support.io.storage.bucket.Temp original;

  /** For debugging leaks if TRACE_BUCKET_LEAKS is enabled */
  private final Throwable tracer;

  private final WeakReference<ToDiskMigratable> weakRef = new WeakReference<>(this);
  protected boolean hasMigrated = false;

  /** If false, there is in-memory storage that needs to be freed. */
  private boolean hasFreedRAM = false;
}
