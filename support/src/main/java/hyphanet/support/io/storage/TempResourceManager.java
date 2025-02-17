package hyphanet.support.io.storage;

import hyphanet.base.TimeUtil;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.bucket.BucketFactory;
import hyphanet.support.io.storage.bucket.RandomAccessible;
import hyphanet.support.io.storage.bucket.TempBucketFactory;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.storage.rab.RabFactory;
import hyphanet.support.io.storage.rab.TempRab;
import hyphanet.support.io.storage.rab.TempRabFactory;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.TimeUnit.MINUTES;

// TODO:  Replace finalizer with Cleaner
public class TempResourceManager
    implements RabFactory, BucketFactory {

  public static final boolean TRACE_STORAGE_LEAKS = false;
  public static final EncryptType CRYPT_TYPE = EncryptType.CHACHA_128;
  /** How many times the maxRamStorageSize can a RAM storage be before it gets migrated? */
  private static final int RAMSTORAGE_CONVERSION_FACTOR = 4;
  /** How old is a long-lived RAM storage? */
  private static final long RAM_STORAGE_MAX_AGE = MINUTES.toMillis(5);

  private static final double MAX_USAGE_LOW = 0.8;
  private static final double MAX_USAGE_HIGH = 0.9;
  private static final Logger logger = LoggerFactory.getLogger(TempResourceManager.class);

  public TempResourceManager(
      ExecutorService executor,
      FilenameGenerator filenameGenerator,
      long maxInitSingleRamStorageSize,
      long ramStoragePoolSize,
      long minDiskSpace,
      boolean encrypt,
      MasterSecret masterSecret) {
    this.maxInitSingleRamStorageSize = maxInitSingleRamStorageSize;
    this.ramStoragePoolSize = ramStoragePoolSize;
    this.executor = executor;

    this.rabFactory =
        new TempRabFactory(
            ramTracker,
            filenameGenerator,
            minDiskSpace - ramStoragePoolSize,
            encrypt,
            CRYPT_TYPE,
            masterSecret);
    this.bucketFactory =
        new TempBucketFactory(
            ramTracker,
            filenameGenerator,
            ramStoragePoolSize * RAMSTORAGE_CONVERSION_FACTOR,
            ramStoragePoolSize,
            minDiskSpace,
            encrypt,
            CRYPT_TYPE,
            masterSecret,
            rabFactory);
  }

  @Override
  public synchronized RandomAccessible makeBucket(long size) throws IOException {
    setCreateRamStorage(size, bucketFactory);
    runCleaner();
    var bucket = bucketFactory.makeBucket(size);
    if (bucketFactory.isCreateRam()) {
      ramTracker.addToRamStorageQueue(bucket);
    }
    return bucket;
  }

  @Override
  public synchronized Rab makeRab(long size) throws IOException {
    setCreateRamStorage(size, rabFactory);
    runCleaner();
    var rab = rabFactory.makeRab(size);
    if (rabFactory.isCreateRam()) {
      ramTracker.addToRamStorageQueue((TempRab) rab);
    }
    return rab;
  }

  @Override
  public synchronized Rab makeRab(
      byte[] initialContents, int offset, int size, boolean readOnly) throws IOException {
    setCreateRamStorage(size, rabFactory);
    runCleaner();
    var rab = rabFactory.makeRab(initialContents, offset, size, readOnly);
    if (rabFactory.isCreateRam()) {
      ramTracker.addToRamStorageQueue((TempRab) rab);
    }
    return rab;
  }

  private synchronized void setCreateRamStorage(long size, RamStorageCapableFactory factory) {
    if ((size > 0)
        && (size <= maxInitSingleRamStorageSize)
        && (ramTracker.getRamBytesInUse() < ramStoragePoolSize)
        && (ramTracker.getRamBytesInUse() + size <= ramStoragePoolSize)) {
      factory.setCreateRam(true);
      ramTracker.takeRam(size);
    }
  }

  private synchronized void runCleaner() {
    if (ramTracker.getRamBytesInUse() >= ramStoragePoolSize * MAX_USAGE_HIGH && !runningCleaner) {
      runningCleaner = true;
      executor.execute(cleaner);
    }
  }

  private final TempRabFactory rabFactory;
  private final TempBucketFactory bucketFactory;

  /**
   * How big can the max initial size be for us to consider using RAM storage? If the initial size
   * is larger than maxInitSingleRamStorageSize, only file bucket will be created. The RAM storage
   * size can be increased later and greater than maxInitSingleRamStorageSize. But the maximum size
   * is {@code maxInitSingleRamStorageSize * RAMSTORAGE_CONVERSION_FACTOR}. If oversize, it will be
   * migrated to a file bucket.
   */
  private final long maxInitSingleRamStorageSize;

  /** How much memory do we dedicate to the RAM storage pool? (in bytes) */
  private final long ramStoragePoolSize;

  private final TempStorageRamTracker ramTracker = new TempStorageRamTracker();

  private final ExecutorService executor;
  private boolean runningCleaner = false;
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
                  saidSo = true;
                }
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e1) {
                  Thread.currentThread().interrupt();
                }
                continue;
              }
              break;
            }
            saidSo = false;
            while (true) {
              // Now migrate buckets until usage is below the lower threshold.
              synchronized (TempResourceManager.this) {
                if (ramTracker.getRamBytesInUse() <= ramStoragePoolSize * MAX_USAGE_LOW) {
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
                  saidSo = true;
                }
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e1) {
                  Thread.currentThread().interrupt();
                }
              }
            }
          } finally {
            synchronized (TempResourceManager.this) {
              runningCleaner = false;
            }
          }
        }

        /**
         * Migrate all long-lived buckets from the queue.
         *
         * @param now The current time (System.currentTimeMillis()).
         * @param force If true, migrate one bucket which isn't necessarily long lived, just to free
         *     up space. Otherwise we will migrate all long-lived buckets but not any others.
         * @return True if we migrated any buckets.
         * @throws InsufficientDiskSpaceException If there is not enough space to migrate buckets to
         *     disk.
         */
        private boolean cleanBucketQueue(long now, boolean force)
            throws InsufficientDiskSpaceException {
          boolean shouldContinue = true;
          // create a new list to avoid race-conditions
          Queue<TempStorage> toMigrate = null;
          logger.info("Starting cleanBucketQueue");
          do {
            synchronized (ramTracker) {
              final WeakReference<TempStorage> tmpBucketRef =
                  ramTracker.getRamStorageQueue().peek();
              if (tmpBucketRef == null) {
                shouldContinue = false;
              } else {
                TempStorage tmpBucket = tmpBucketRef.get();
                if (tmpBucket == null) {
                  ramTracker.removeFromRamStorageQueue(tmpBucketRef);
                  continue; // ugh. this is freed
                }

                // Don't access the buckets inside the lock, will deadlock.
                if (tmpBucket.creationTime() + RAM_STORAGE_MAX_AGE > now && !force) {
                  shouldContinue = false;
                } else {
                  logger
                      .atInfo()
                      .setMessage("The bucket {} is {} old: we will force-migrate it to disk.")
                      .addArgument(tmpBucket)
                      .addArgument(() -> TimeUtil.formatTime(now - tmpBucket.creationTime()))
                      .log();
                  ramTracker.removeFromRamStorageQueue(tmpBucketRef);
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
            for (TempStorage tmpBucket : toMigrate) {
              try {
                tmpBucket.migrateToDisk();
              } catch (InsufficientDiskSpaceException e) {
                throw e;
              } catch (IOException e) {
                logger.error(
                    "An IOE occured while migrating long-lived buckets:{}", e.getMessage(), e);
              }
            }
            return true;
          }
          return false;
        }
      };
}
