package hyphanet.support.io.storage;

import static java.util.concurrent.TimeUnit.MINUTES;

import hyphanet.base.TimeUtil;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.bucket.BucketFactory;
import hyphanet.support.io.storage.bucket.TempBucket;
import hyphanet.support.io.storage.bucket.TempBucketFactory;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.storage.rab.RabFactory;
import hyphanet.support.io.storage.rab.TempRabFactory;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempStorageManager implements RabFactory, BucketFactory {

  public static final boolean TRACE_STORAGE_LEAKS = false;

  /** How many times the maxRamStorageSize can a RAM storage be before it gets migrated? */
  public static final int RAMSTORAGE_CONVERSION_FACTOR = 4;

  /** How old is a long-lived RAM storage? */
  private static final long RAM_STORAGE_MAX_AGE = MINUTES.toMillis(5);

  private static final double MAX_USAGE_LOW = 0.8;
  private static final double MAX_USAGE_HIGH = 0.9;
  private static final Logger logger = LoggerFactory.getLogger(TempStorageManager.class);

  public TempStorageManager(
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
            Storage.CRYPT_TYPE,
            masterSecret);
    this.bucketFactory =
        new TempBucketFactory(
            ramTracker,
            filenameGenerator,
            maxInitSingleRamStorageSize * RAMSTORAGE_CONVERSION_FACTOR,
            ramStoragePoolSize,
            minDiskSpace,
            encrypt,
            Storage.CRYPT_TYPE,
            masterSecret,
            rabFactory);
  }

  @Override
  public synchronized TempBucket makeBucket(long size) throws IOException {
    setCreateRamStorage(size, bucketFactory);
    runRamReleaser();
    return bucketFactory.makeBucket(size);
  }

  @Override
  public synchronized Rab makeRab(long size) throws IOException {
    setCreateRamStorage(size, rabFactory);
    runRamReleaser();
    return rabFactory.makeRab(size);
  }

  @Override
  public synchronized Rab makeRab(byte[] initialContents, int offset, int size, boolean readOnly)
      throws IOException {
    setCreateRamStorage(size, rabFactory);
    runRamReleaser();
    return rabFactory.makeRab(initialContents, offset, size, readOnly);
  }

  public TempRabFactory getRabFactory() {
    return rabFactory;
  }

  public TempBucketFactory getBucketFactory() {
    return bucketFactory;
  }

  public void setEncrypt(boolean encrypt) {
    bucketFactory.setEncrypt(encrypt);
    rabFactory.setEncrypt(encrypt);
  }

  public TempStorageTracker getRamTracker() {
    return ramTracker;
  }

  private synchronized void setCreateRamStorage(long size, RamStorageCapableFactory factory) {
    if ((size > 0)
        && (size <= maxInitSingleRamStorageSize)
        && (ramTracker.getRamBytesInUse() < ramStoragePoolSize)
        && (ramTracker.getRamBytesInUse() + size <= ramStoragePoolSize)) {
      factory.setCreateRam(true);
    }
  }

  private synchronized void runRamReleaser() {
    if (ramTracker.getRamBytesInUse() >= ramStoragePoolSize * MAX_USAGE_HIGH
        && !runningRamReleaser) {
      runningRamReleaser = true;
      executor.execute(ramReleaser);
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

  private final TempStorageTracker ramTracker = new TempStorageTracker();

  private final ExecutorService executor;
  private boolean runningRamReleaser = false;
  private final Runnable ramReleaser =
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
              synchronized (TempStorageManager.this) {
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
            synchronized (TempStorageManager.this) {
              runningRamReleaser = false;
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
          List<TempStorage> toMigrate = null;
          logger.info("Starting cleanBucketQueue");
          do {
            synchronized (ramTracker) {
              final var tempStorage = ramTracker.peakQueue();
              if (tempStorage == null) {
                shouldContinue = false;
              } else {
                if (!tempStorage.isRamStorage()) {
                  ramTracker.removeFromQueue(tempStorage);
                  continue;
                }

                // Don't access the buckets inside the lock, will deadlock.
                if (tempStorage.creationTime() + RAM_STORAGE_MAX_AGE > now && !force) {
                  shouldContinue = false;
                } else {
                  logger
                      .atInfo()
                      .setMessage("The storage {} is {} old: we will force-migrate it to disk.")
                      .addArgument(tempStorage)
                      .addArgument(() -> TimeUtil.formatTime(now - tempStorage.creationTime()))
                      .log();
                  ramTracker.removeFromQueue(tempStorage);
                  if (toMigrate == null) {
                    toMigrate = new ArrayList<>();
                  }
                  toMigrate.add(tempStorage);
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
