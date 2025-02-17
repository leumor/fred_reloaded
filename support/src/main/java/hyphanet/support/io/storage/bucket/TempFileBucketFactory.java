package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.EncryptType;
import hyphanet.support.io.storage.bucket.wrapper.EncryptedBucket;
import hyphanet.support.io.storage.bucket.wrapper.PaddedRandomAccessBucket;

import java.io.IOException;

public class TempFileBucketFactory implements BucketFactory {

  public TempFileBucketFactory(
      FilenameGenerator filenameGenerator,
      boolean encrypt,
      EncryptType encryptType,
      MasterSecret secret) {
    this.filenameGenerator = filenameGenerator;
    this.encrypt = encrypt;
    this.encryptType = encryptType;
    this.secret = secret;
  }

  @Override
  public RandomAccessible makeBucket(long size) throws IOException {
    RandomAccessible ret =
        new TempFileBucket(filenameGenerator.makeRandomFilename(), filenameGenerator, true);
    // Do we want it to be encrypted?
    if (encrypt) {
      ret = new PaddedRandomAccessBucket(ret);
      ret = new EncryptedBucket(encryptType, ret, secret);
    }
    return ret;
  }

  private final FilenameGenerator filenameGenerator;
  private final boolean encrypt;
  private final EncryptType encryptType;
  private final MasterSecret secret;
}
