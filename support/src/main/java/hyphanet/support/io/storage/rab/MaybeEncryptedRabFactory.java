package hyphanet.support.io.storage.rab;

import static hyphanet.support.io.storage.Storage.CRYPT_TYPE;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.storage.bucket.wrapper.PaddedEphemerallyEncryptedBucket;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RabFactory} decorator that conditionally encrypts {@link Rab} instances based on a flag
 * and the availability of a {@link MasterSecret}.
 *
 * <p>This factory wraps another {@link RabFactory} and decides whether to apply encryption to the
 * {@link Rab} objects it creates. Encryption is enabled if the 'encrypt' flag is set to true and a
 * {@link MasterSecret} is available via {@link #setMasterSecret(MasterSecret)}. If encryption is
 * enabled, the created {@link Rab} will be an {@link EncryptedRab}; otherwise, it will be created
 * by the underlying factory directly.
 */
public class MaybeEncryptedRabFactory implements RabFactory {

  private static final Logger logger = LoggerFactory.getLogger(MaybeEncryptedRabFactory.class);

  /**
   * Constructs a {@link MaybeEncryptedRabFactory}.
   *
   * @param factory The underlying {@link RabFactory} to delegate to for {@link Rab} creation.
   * @param encrypt A boolean flag indicating whether encryption should be enabled if possible.
   */
  public MaybeEncryptedRabFactory(RabFactory factory, boolean encrypt) {
    this.factory = factory;
    this.reallyEncrypt = encrypt;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a {@link Rab} of the specified size, potentially with encryption and padding. If
   * encryption is enabled and a {@link MasterSecret} is set, this method will:
   *
   * <ol>
   *   <li>Calculate the real size including encryption header overhead.
   *   <li>Calculate a padded size to meet minimum padding requirements for encrypted data.
   *   <li>Delegate the actual {@link Rab} creation to the underlying {@link RabFactory} with the
   *       padded size.
   *   <li>If padding was applied, wrap the created {@link Rab} in a {@link PaddedRab}.
   *   <li>Wrap the (potentially padded) {@link Rab} in an {@link EncryptedRab} for encryption.
   * </ol>
   *
   * If encryption is not enabled or no {@link MasterSecret} is available, it simply delegates the
   * {@link Rab} creation to the underlying factory with the requested size.
   *
   * @throws IOException If an I/O error occurs during {@link Rab} creation.
   * @throws IllegalArgumentException If size is negative (delegated from underlying factory).
   */
  @Override
  public Rab makeRab(long size) throws IOException {
    long realSize = size;
    long paddedSize = size;
    MasterSecret secretToUse = null;
    synchronized (this) {
      if (reallyEncrypt && this.secret != null) {
        secretToUse = this.secret;
        realSize += CRYPT_TYPE.headerLen;
        paddedSize =
            PaddedEphemerallyEncryptedBucket.paddedLength(
                realSize, PaddedEphemerallyEncryptedBucket.MIN_PADDED_SIZE);
        logger.info("Encrypting and padding {} to {}", size, paddedSize);
      }
    }
    Rab raf = factory.makeRab(paddedSize);
    if (secretToUse != null) {
      if (realSize != paddedSize) {
        raf = new PaddedRab(raf, realSize);
      }
      try {
        raf = new EncryptedRab(CRYPT_TYPE, raf, secretToUse, true);
      } catch (GeneralSecurityException e) {
        logger.error("Cannot create encrypted temp file: {}", e, e);
      }
    }
    return raf;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a {@link Rab} with initial content, potentially with encryption.
   *
   * <p>If encryption is enabled, this method will:
   *
   * <ol>
   *   <li>Create a {@link Rab} of the specified size using {@link #makeRab(long)}.
   *   <li>Write the {@code initialContents} to the newly created {@link Rab}.
   *   <li>If {@code readOnly} is true, wrap the {@link Rab} in a {@link ReadOnlyRab}.
   * </ol>
   *
   * This approach currently performs encryption after writing the initial content to the {@link
   * Rab}. Consider in-memory encryption for potential performance improvements, especially for
   * large initial contents.
   *
   * <p>If encryption is disabled, it delegates the {@link Rab} creation directly to the underlying
   * factory.
   *
   * @throws IOException If an I/O error occurs during {@link Rab} creation or writing initial
   *     content.
   */
  @Override
  public Rab makeRab(byte[] initialContents, int offset, int size, boolean readOnly)
      throws IOException {
    if (reallyEncrypt) {
      // FIXME do the encryption in memory? Test it ...
      Rab ret = makeRab(size);
      ret.pwrite(0, initialContents, offset, size);
      if (readOnly) {
        ret = new ReadOnlyRab(ret);
      }
      return ret;
    } else {
      return factory.makeRab(initialContents, offset, size, readOnly);
    }
  }

  /**
   * Sets the {@link MasterSecret} to be used for encryption.
   *
   * <p>This secret is required for encryption to be actually applied. If encryption is enabled via
   * {@link #setEncryption(boolean)} but no {@link MasterSecret} is set, no encryption will be
   * performed.
   *
   * @param secret The {@link MasterSecret} to use for encryption, or {@code null} to disable
   *     encryption even if the 'encrypt' flag is set.
   */
  public void setMasterSecret(@Nullable MasterSecret secret) {
    this.secret = secret;
  }

  /**
   * Enables or disables encryption for subsequently created {@link Rab} instances.
   *
   * <p>Note that encryption is only applied if both this flag is set to {@code true} and a {@link
   * MasterSecret} has been set via {@link #setMasterSecret(MasterSecret)}.
   *
   * @param value {@code true} to enable encryption if a {@link MasterSecret} is available, {@code
   *     false} to disable encryption.
   */
  public void setEncryption(boolean value) {
    reallyEncrypt = value;
  }

  /** The underlying {@link RabFactory} used for actual {@link Rab} creation. */
  private final RabFactory factory;

  /**
   * A volatile flag indicating whether encryption should be applied if possible.
   *
   * <p>This flag is set by {@link #setEncryption(boolean)}. Encryption is only applied if this is
   * {@code true} and a {@link MasterSecret} is available.
   */
  private volatile boolean reallyEncrypt;

  /**
   * The {@link MasterSecret} used for encryption.
   *
   * <p>This secret is set by {@link #setMasterSecret(MasterSecret)}. If this is {@code null}, no
   * encryption will be performed even if {@link #reallyEncrypt} is {@code true}.
   */
  private @Nullable MasterSecret secret;
}
