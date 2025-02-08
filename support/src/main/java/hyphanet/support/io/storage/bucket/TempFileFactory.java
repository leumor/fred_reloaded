package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.EncryptType;
import java.io.IOException;

public class TempFileFactory implements Factory {

  public TempFileFactory(
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
        new TempFile(filenameGenerator.makeRandomFilename(), filenameGenerator, true);
    // Do we want it to be encrypted?
    if (encrypt) {
      ret = new PaddedRandomAccess(ret);
      ret = new Encrypted(encryptType, ret, secret);
    }
    return ret;
  }

  private final FilenameGenerator filenameGenerator;
  private final boolean encrypt;
  private final EncryptType encryptType;
  private final MasterSecret secret;
}
