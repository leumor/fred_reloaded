package hyphanet.support.io.storage.rab;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.storage.bucket.TempBucketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** Wraps another RandomAccessBufferFactory to enable encryption if currently turned on. */
public class MaybeEncryptedRandomAccessBufferFactory implements RabFactory {

  private static final Logger logger =
      LoggerFactory.getLogger(MaybeEncryptedRandomAccessBufferFactory.class);

  public MaybeEncryptedRandomAccessBufferFactory(RabFactory factory, boolean encrypt) {
    this.factory = factory;
    this.reallyEncrypt = encrypt;
  }

  @Override
  public Rab makeRab(long size) throws IOException {
    long realSize = size;
    long paddedSize = size;
    MasterSecret secret = null;
    synchronized (this) {
      if (reallyEncrypt && this.secret != null) {
        secret = this.secret;
        realSize += TempBucketFactory.CRYPT_TYPE.headerLen;
        paddedSize =
            PaddedEphemerallyEncryptedBucket.paddedLength(
                realSize, PaddedEphemerallyEncryptedBucket.MIN_PADDED_SIZE);
        if (logMINOR) {
          Logger.minor(this, "Encrypting and padding " + size + " to " + paddedSize);
        }
      }
    }
    Rab raf = factory.makeRab(paddedSize);
    if (secret != null) {
      if (realSize != paddedSize) {
        raf = new PaddedRab(raf, realSize);
      }
      try {
        raf = new EncryptedRab(TempBucketFactory.CRYPT_TYPE, raf, secret, true);
      } catch (GeneralSecurityException e) {
        Logger.error(this, "Cannot create encrypted tempfile: " + e, e);
      }
    }
    return raf;
  }

  @Override
  public Rab makeRAF(byte[] initialContents, int offset, int size, boolean readOnly)
      throws IOException {
    boolean reallyEncrypt = false;
    synchronized (this) {
      reallyEncrypt = this.reallyEncrypt;
    }
    if (reallyEncrypt) {
      // FIXME do the encryption in memory? Test it ...
      Rab ret = makeRAF(size);
      ret.pwrite(0, initialContents, offset, size);
      if (readOnly) {
        ret = new ReadOnlyRab(ret);
      }
      return ret;
    } else {
      return factory.makeRAF(initialContents, offset, size, readOnly);
    }
  }

  public void setMasterSecret(MasterSecret secret) {
    synchronized (this) {
      this.secret = secret;
    }
  }

  public void setEncryption(boolean value) {
    synchronized (this) {
      reallyEncrypt = value;
    }
    if (factory instanceof PooledFileRabFactory) {
      ((PooledFileRabFactory) factory).enableCrypto(value);
    }
  }

  private final RabFactory factory;
  private volatile boolean reallyEncrypt;
  private MasterSecret secret;
}
