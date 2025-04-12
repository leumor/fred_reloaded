package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.EncryptType;
import hyphanet.support.io.storage.RamStorageCapableFactory;
import hyphanet.support.io.storage.TempStorageTracker;
import hyphanet.support.io.storage.rab.RabFactory;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;

import java.io.IOException;
import java.nio.file.Files;

public class TempBucketFactory implements BucketFactory, RamStorageCapableFactory {

  public TempBucketFactory(
      TempStorageTracker ramTracker,
      FilenameGenerator filenameGenerator,
      long maxRamSize,
      long ramStoragePoolSize,
      long minDiskSpace,
      boolean encrypt,
      EncryptType encryptType,
      MasterSecret secret,
      RabFactory rabMigrateToFactory) {
    this.ramTracker = ramTracker;
    this.filenameGenerator = filenameGenerator;
    this.maxRamSize = maxRamSize;
    this.ramStoragePoolSize = ramStoragePoolSize;
    this.minDiskSpace = minDiskSpace;
    this.encrypt = encrypt;
    this.encryptType = encryptType;
    this.secret = secret;
    this.rabMigrateToFactory = rabMigrateToFactory;
  }

  /**
   * Create a temp bucket
   *
   * @param size Maximum size
   * @return A temporary Bucket
   * @exception IOException If it is not possible to create a temp bucket due to an I/O error
   */
  @Override
  public TempBucket makeBucket(long size) throws IOException {
    RandomAccessBucket realBucket;
    long now = System.currentTimeMillis();

    var tempFileBucketFactory =
        new TempFileBucketFactory(filenameGenerator, encrypt, encryptType, secret);

    // Do we want a RAMBucket or a FileBucket?
    realBucket = (createRam ? new ArrayBucket() : tempFileBucketFactory.makeBucket(size));

    var toReturn =
        new TempBucket(
            ramTracker,
            now,
            realBucket,
            filenameGenerator.getDir(),
            maxRamSize,
            ramStoragePoolSize,
            minDiskSpace,
            tempFileBucketFactory,
            rabMigrateToFactory);
    if (!createRam // No need to consider them for migration if they can't be migrated
        && size != -1
        && size != Long.MAX_VALUE // If we know the disk space requirement in advance, check it.
        && Files.getFileStore(filenameGenerator.getDir()).getUsableSpace() + size < minDiskSpace) {
      throw new InsufficientDiskSpaceException();
    }

    return toReturn;
  }

  public boolean isCreateRam() {
    return createRam;
  }

  @Override
  public void setCreateRam(boolean createRam) {
    this.createRam = createRam;
  }

  public boolean isEncrypt() {
    return encrypt;
  }

  public void setEncrypt(boolean encrypt) {
    this.encrypt = encrypt;
  }

  private final TempStorageTracker ramTracker;
  private final FilenameGenerator filenameGenerator;
  private final EncryptType encryptType;
  private final MasterSecret secret;

  /**
   * The maximum RAM size allowed for this bucket. If over this size, the bucket will be migrated to
   * a file bucket.
   */
  private final long maxRamSize;

  private final long ramStoragePoolSize;
  private final long minDiskSpace;
  private final RabFactory rabMigrateToFactory;
  private boolean encrypt;
  private boolean createRam;
}
