/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import hyphanet.base.TimeUtil;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.ToDiskMigratable;
import hyphanet.support.io.storage.randomaccessbuffer.Padded;
import hyphanet.support.io.storage.randomaccessbuffer.*;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Temporary Bucket Factory
 *
 * <p>Buckets created by this factory can be either: - ArrayBuckets OR - FileBuckets
 *
 * <p>ArrayBuckets are used if and only if: 1) there is enough room remaining on the pool (@see
 * maxRamUsed and @see bytesInUse) 2) the initial size is smaller than (@maxRAMBucketSize)
 *
 * <p>Depending on how they are used they might switch from one type to another transparently.
 *
 * <p>Currently they are two factors considered for a migration: - if they are long-lived or not
 * (@see RAMBUCKET_MAX_AGE) - if their size is over RAMBUCKET_CONVERSION_FACTOR*maxRAMBucketSize
 */
public class TempBucketFactory implements Factory,
    hyphanet.support.io.storage.randomaccessbuffer.Factory {
  public static final long defaultIncrement = 4096;
  public static final float DEFAULT_FACTOR = 1.25F;
  public static final hyphanet.support.io.storage.randomaccessbuffer.Encrypted.Type CRYPT_TYPE =
      hyphanet.support.io.storage.randomaccessbuffer.Encrypted.Type.CHACHA_128;
  public static final boolean TRACE_BUCKET_LEAKS = false;
  public final Queue<WeakReference<ToDiskMigratable>> ramBucketQueue = new LinkedBlockingQueue<>();

  /** How many times the maxRAMBucketSize can a RAMBucket be before it gets migrated? */
  static final int RAMBUCKET_CONVERSION_FACTOR = 4;

  static final double MAX_USAGE_LOW = 0.8;
  static final double MAX_USAGE_HIGH = 0.9;

  /** How old is a long-lived RAMBucket? */
  private static final long RAMBUCKET_MAX_AGE = MINUTES.toMillis(5);

  private static final Logger logger = LoggerFactory.getLogger(TempBucketFactory.class);

  // Storage accounting disabled by default.
  public TempBucketFactory(
      ExecutorService executor,
      FilenameGenerator filenameGenerator,
      long maxBucketSizeKeptInRam,
      long maxRamUsed,
      long minDiskSpace,
      MasterSecret masterSecret) {
    this.filenameGenerator = filenameGenerator;
    this.maxRamUsed = maxRamUsed;
    this.maxRAMBucketSize = maxBucketSizeKeptInRam;
    this.reallyEncrypt = reallyEncrypt;
    this.executor = executor;
    this.underlyingDiskRabFactory = new PooledFileFactory(filenameGenerator);
    this.minDiskSpace = minDiskSpace;
    this.diskRabFactory =
        new DiskSpaceCheckingFactory(
            underlyingDiskRabFactory, filenameGenerator.getDir(), minDiskSpace - maxRamUsed);
    this.secret = masterSecret;
  }

  @Override
  public RandomAccessible makeBucket(long size) throws IOException {
    return makeBucket(size, DEFAULT_FACTOR, defaultIncrement);
  }

  public RandomAccessible makeBucket(long size, float factor) throws IOException {
    return makeBucket(size, factor, defaultIncrement);
  }

  public synchronized long getRamUsed() {
    return bytesInUse;
  }

  public synchronized long getMaxRamUsed() {
    return maxRamUsed;
  }

  public synchronized void setMaxRamUsed(long size) {
    maxRamUsed = size;
  }

  public synchronized long getMaxRamBucketSize() {
    return maxRAMBucketSize;
  }

  public synchronized void setMaxRAMBucketSize(long size) {
    maxRAMBucketSize = size;
    diskRabFactory.setMinDiskSpace(minDiskSpace - maxRamUsed);
  }

  public void setEncryption(boolean value) {
    reallyEncrypt = value;
  }

  public boolean isEncrypting() {
    return reallyEncrypt;
  }

  /**
   * Create a temp bucket
   *
   * @param size Maximum size
   * @param factor Factor to increase size by when need more space
   * @return A temporary Bucket
   * @throws IOException If it is not possible to create a temp bucket due to an I/O error
   */
  public Temp makeBucket(long size, float factor, long increment) throws IOException {
    RandomAccessible realBucket = null;
    boolean useRAMBucket = false;
    long now = System.currentTimeMillis();

    synchronized (this) {
      if ((size > 0)
          && (size <= maxRAMBucketSize)
          && (bytesInUse < maxRamUsed)
          && (bytesInUse + size <= maxRamUsed)) {
        useRAMBucket = true;
      }
      if (bytesInUse >= maxRamUsed * MAX_USAGE_HIGH && !runningCleaner) {
        runningCleaner = true;
        executor.execute(cleaner);
      }
    }

    // Do we want a RAMBucket or a FileBucket?
    realBucket = (useRAMBucket ? new Array() : _makeFileBucket());

    Temp toReturn = new Temp(this, now, realBucket);
    if (useRAMBucket) { // No need to consider them for migration if they can't be migrated
      synchronized (ramBucketQueue) {
        ramBucketQueue.add(toReturn.getReference());
      }
    } else {
      // If we know the disk space requirement in advance, check it.
      if (size != -1
          && size != Long.MAX_VALUE
          && Files.getFileStore(filenameGenerator.getDir()).getUsableSpace() + size
              < minDiskSpace) {
        throw new InsufficientDiskSpaceException();
      }
    }
    return toReturn;
  }

  @Override
  public RandomAccessBuffer makeRab(long size) throws IOException {
    if (size < 0) {
      throw new IllegalArgumentException();
    }
    if (size > Integer.MAX_VALUE) {
      return diskRabFactory.makeRab(size);
    }

    long now = System.currentTimeMillis();

    hyphanet.support.io.storage.randomaccessbuffer.Temp raf = null;

    synchronized (this) {
      if ((size > 0)
          && (size <= maxRAMBucketSize)
          && (bytesInUse < maxRamUsed)
          && (bytesInUse + size <= maxRamUsed)) {
        raf = new hyphanet.support.io.storage.randomaccessbuffer.Temp(this, (int) size, now);
        bytesInUse += size;
      }
      if (bytesInUse >= maxRamUsed * MAX_USAGE_HIGH && !runningCleaner) {
        runningCleaner = true;
        executor.execute(cleaner);
      }
    }

    if (raf != null) {
      synchronized (ramBucketQueue) {
        ramBucketQueue.add(raf.getReference());
      }
      return raf;
    } else {
      boolean encrypt;
      encrypt = this.reallyEncrypt;
      long realSize = size;
      long paddedSize = size;
      if (encrypt) {
        realSize += TempBucketFactory.CRYPT_TYPE.headerLen;
        paddedSize =
            PaddedEphemerallyEncrypted.paddedLength(
                realSize, PaddedEphemerallyEncrypted.MIN_PADDED_SIZE);
      }
      RandomAccessBuffer ret = diskRabFactory.makeRab(paddedSize);
      if (encrypt) {
        if (realSize != paddedSize) {
          ret = new Padded(ret, realSize);
        }
        try {
          ret = new hyphanet.support.io.storage.randomaccessbuffer.Encrypted(CRYPT_TYPE, ret, secret, true);
        } catch (GeneralSecurityException e) {
          logger.error("Cannot create encrypted tempfile: {}", e, e);
        }
      }
      return ret;
    }
  }

  @Override
  public RandomAccessBuffer makeRab(byte[] initialContents, int offset, int size, boolean readOnly)
      throws IOException {
    if (size < 0) {
      throw new IllegalArgumentException();
    }

    long now = System.currentTimeMillis();

    hyphanet.support.io.storage.randomaccessbuffer.Temp raf = null;

    synchronized (this) {
      if ((size > 0)
          && (size <= maxRAMBucketSize)
          && (bytesInUse < maxRamUsed)
          && (bytesInUse + size <= maxRamUsed)) {
        raf =
            new hyphanet.support.io.storage.randomaccessbuffer.Temp(
                this, initialContents, offset, size, now, readOnly);
        bytesInUse += size;
      }
      if (bytesInUse >= maxRamUsed * MAX_USAGE_HIGH && !runningCleaner) {
        runningCleaner = true;
        executor.execute(cleaner);
      }
    }

    if (raf != null) {
      synchronized (ramBucketQueue) {
        ramBucketQueue.add(raf.getReference());
      }
      return raf;
    } else {
      if (reallyEncrypt) {
        // FIXME do the encryption in memory? Test it ...
        RandomAccessBuffer ret = makeRab(size);
        ret.pwrite(0, initialContents, offset, size);
        if (readOnly) {
          ret = new ReadOnly(ret);
        }
        return ret;
      }
      return diskRabFactory.makeRab(initialContents, offset, size, readOnly);
    }
  }

  public DiskSpaceCheckingFactory getUnderlyingRabFactory() {
    return diskRabFactory;
  }

  public synchronized void _hasDisposed(long size) {
    bytesInUse -= size;
  }

  public RandomAccessible _makeFileBucket() throws IOException {
    RandomAccessible ret =
        new TempFile(filenameGenerator.makeRandomFilename(), filenameGenerator, true);
    // Do we want it to be encrypted?
    if (reallyEncrypt) {
      ret = new PaddedRandomAccess(ret);
      ret = new Encrypted(CRYPT_TYPE, ret, secret);
    }
    return ret;
  }

  public synchronized void _hasTaken(long size) {
    bytesInUse += size;
  }

  public FilenameGenerator getFilenameGenerator() {
    return filenameGenerator;
  }

  public long getMinDiskSpace() {
    return minDiskSpace;
  }

  public synchronized void setMinDiskSpace(long min) {
    minDiskSpace = min;
    diskRabFactory.setMinDiskSpace(minDiskSpace - maxRamUsed);
  }

  /**
   * Migrate all long-lived buckets from the queue.
   *
   * @param now The current time (System.currentTimeMillis()).
   * @param force If true, migrate one bucket which isn't necessarily long lived, just to free up
   *     space. Otherwise we will migrate all long-lived buckets but not any others.
   * @return True if we migrated any buckets.
   * @throws InsufficientDiskSpaceException If there is not enough space to migrate buckets to disk.
   */
  private boolean cleanBucketQueue(long now, boolean force) throws InsufficientDiskSpaceException {
    boolean shouldContinue = true;
    // create a new list to avoid race-conditions
    Queue<ToDiskMigratable> toMigrate = null;
    logger.info("Starting cleanBucketQueue");
    do {
      synchronized (ramBucketQueue) {
        final WeakReference<ToDiskMigratable> tmpBucketRef = ramBucketQueue.peek();
        if (tmpBucketRef == null) {
          shouldContinue = false;
        } else {
          ToDiskMigratable tmpBucket = tmpBucketRef.get();
          if (tmpBucket == null) {
            ramBucketQueue.remove(tmpBucketRef);
            continue; // ugh. this is freed
          }

          // Don't access the buckets inside the lock, will deadlock.
          if (tmpBucket.creationTime() + RAMBUCKET_MAX_AGE > now && !force) {
            shouldContinue = false;
          } else {
            logger
                .atInfo()
                .setMessage("The bucket {} is {} old: we will force-migrate it to disk.")
                .addArgument(tmpBucket)
                .addArgument(() -> TimeUtil.formatTime(now - tmpBucket.creationTime()))
                .log();
            ramBucketQueue.remove(tmpBucketRef);
            if (toMigrate == null) {
              toMigrate = new LinkedList<>();
            }
            toMigrate.add(tmpBucket);
            force = false;
          }
        }
      }
    } while (shouldContinue);

    if (toMigrate == null) {
      return false;
    }
    if (!toMigrate.isEmpty()) {
      logger.info("We are going to migrate {} RAMBuckets", toMigrate.size());
      for (ToDiskMigratable tmpBucket : toMigrate) {
        try {
          tmpBucket.migrateToDisk();
        } catch (InsufficientDiskSpaceException e) {
          throw e;
        } catch (IOException e) {
          logger.error("An IOE occured while migrating long-lived buckets:{}", e.getMessage(), e);
        }
      }
      return true;
    }
    return false;
  }

  private final FilenameGenerator filenameGenerator;
  private final ExecutorService executor;
  private final MasterSecret secret;
  boolean runningCleaner = false;
  private volatile long minDiskSpace;
  private long bytesInUse = 0;
  private volatile boolean reallyEncrypt;

  /** How big can the defaultSize be for us to consider using RAMBuckets? */
  private long maxRAMBucketSize;

  /** How much memory do we dedicate to the RAMBucketPool? (in bytes) */
  private long maxRamUsed;

  private final Runnable cleaner =
      new Runnable() {

        @Override
        public void run() {
          boolean saidSo = false;
          try {
            long now = System.currentTimeMillis();
            // First migrate all the old buckets.
            while (true) {
              try {
                cleanBucketQueue(now, false);
              } catch (InsufficientDiskSpaceException e) {
                if (!saidSo) {
                  logger.error("Insufficient disk space to migrate in-RAM buckets to disk!");
                  System.err.println("Out of disk space!");
                  saidSo = true;
                }
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e1) {
                  // Ignore.
                }
                continue;
              }
              break;
            }
            saidSo = false;
            while (true) {
              // Now migrate buckets until usage is below the lower threshold.
              synchronized (TempBucketFactory.this) {
                if (bytesInUse <= maxRamUsed * MAX_USAGE_LOW) {
                  return;
                }
              }
              try {
                if (!cleanBucketQueue(System.currentTimeMillis(), true)) {
                  return;
                }
              } catch (InsufficientDiskSpaceException e) {
                if (!saidSo) {
                  logger.error("Insufficient disk space to migrate in-RAM buckets to disk!");
                  System.err.println("Out of disk space!");
                  saidSo = true;
                }
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e1) {
                  // Ignore.
                }
              }
            }
          } finally {
            synchronized (TempBucketFactory.this) {
              runningCleaner = false;
            }
          }
        }
      };
}
