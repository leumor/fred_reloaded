package hyphanet.support.io.storage.randomaccessbuffer;

import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.bucket.PaddedEphemerallyEncrypted;
import hyphanet.support.io.storage.bucket.TempBucketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TempFactory implements Factory {
  private static final Logger logger = LoggerFactory.getLogger(TempFactory.class);

  public TempFactory(
      FilenameGenerator filenameGenerator,
      long maxRamRafSize,
      long ramPoolSize,
      long minDiskSpace,
      boolean encrypt) {
    this.underlyingDiskRabFactory = new PooledFileFactory(filenameGenerator);
    this.maxRamRafSize = maxRamRafSize;
    this.ramPoolSize = ramPoolSize;
    this.minDiskSpace = minDiskSpace;
    this.diskRabFactory =
        new DiskSpaceCheckingFactory(
            underlyingDiskRabFactory, filenameGenerator.getDir(), minDiskSpace - maxRamRafSize);
    this.encrypt = encrypt;
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

    Temp raf = null;

    synchronized (this) {
      if ((size > 0)
          && (size <= maxRamRafSize)
          && (ramBytesInUse < ramPoolSize)
          && (ramBytesInUse + size <= ramPoolSize)) {
        raf = new Temp(this, (int) size, now);
        ramBytesInUse += size;
      }
    }

    if (raf != null) {
      return raf;
    } else {
      boolean encrypt;
      encrypt = this.encrypt;
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
          ret = new Encrypted(CRYPT_TYPE, ret, secret, true);
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

    Temp raf = null;

    synchronized (this) {
      if ((size > 0)
          && (size <= maxRamRafSize)
          && (ramBytesInUse < ramPoolSize)
          && (ramBytesInUse + size <= ramPoolSize)) {
        raf = new Temp(this, initialContents, offset, size, now, readOnly);
        ramBytesInUse += size;
      }
    }

    if (raf != null) {
      return raf;
    } else {
      if (encrypt) {
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

  private final PooledFileFactory underlyingDiskRabFactory;
  private final DiskSpaceCheckingFactory diskRabFactory;

  private final long maxRamRafSize;
  private final long ramPoolSize;
  private final long minDiskSpace;
  private final boolean encrypt;
  private long ramBytesInUse;
}
