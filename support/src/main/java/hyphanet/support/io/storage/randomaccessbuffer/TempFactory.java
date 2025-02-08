package hyphanet.support.io.storage.randomaccessbuffer;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.EncryptType;
import hyphanet.support.io.storage.RamStorageCapableFactory;
import hyphanet.support.io.storage.TempStorageRamTracker;
import hyphanet.support.io.storage.bucket.PaddedEphemerallyEncrypted;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempFactory implements Factory, RamStorageCapableFactory {
  private static final Logger logger = LoggerFactory.getLogger(TempFactory.class);

  public TempFactory(
      TempStorageRamTracker ramTracker,
      FilenameGenerator filenameGenerator,
      long minDiskSpace,
      boolean encrypt,
      EncryptType encryptType,
      MasterSecret secret) {
    this.ramTracker = ramTracker;

    var underlyingDiskRabFactory = new PooledFileFactory(filenameGenerator);
    this.diskRabFactory =
        new DiskSpaceCheckingFactory(
            underlyingDiskRabFactory, filenameGenerator.getDir(), minDiskSpace);

    this.encrypt = encrypt;
    this.encryptType = encryptType;
    this.secret = secret;
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

    if (createRam) {
      return new Temp(ramTracker, (int) size, now, diskRabFactory);
    } else {
      long realSize = size;
      long paddedSize = size;
      if (encrypt) {
        realSize += encryptType.headerLen;
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
          ret = new Encrypted(encryptType, ret, secret, true);
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

    if (createRam) {
      return new Temp(ramTracker, initialContents, offset, size, now, diskRabFactory, readOnly);
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

  public boolean isCreateRam() {
    return createRam;
  }

  public void setCreateRam(boolean createRam) {
    this.createRam = createRam;
  }

  private final TempStorageRamTracker ramTracker;
  private final DiskSpaceCheckingFactory diskRabFactory;
  private final boolean encrypt;
  private final EncryptType encryptType;
  private final MasterSecret secret;
  private boolean createRam;
}
