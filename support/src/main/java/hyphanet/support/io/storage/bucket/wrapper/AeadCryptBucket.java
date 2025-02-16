package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.crypt.Global;
import hyphanet.crypt.io.AeadInputStream;
import hyphanet.crypt.io.AeadOutputStream;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketTools;
import java.io.*;
import java.util.Arrays;
import java.util.Set;

/**
 * Encrypted and authenticated {@link Bucket} implementation using AES in OCB mode (AEAD). Provides
 * confidentiality, integrity, and authenticity of stored data.
 *
 * <p>WARNING: Avoid prematurely closing InputStreams before reaching EOF. MAC verification occurs
 * either when the stream is fully read or during close(), depending on read pattern. Premature
 * closure may bypass authentication checks.
 *
 * <p>Serialization format structure:
 *
 * <ul>
 *   <li>Magic number (4 bytes)
 *   <li>Version (4 bytes)
 *   <li>Key length (1 byte)
 *   <li>Key bytes (variable)
 *   <li>Read-only flag (1 byte)
 *   <li>Underlying bucket data
 * </ul>
 *
 * @author toad
 */
public class AeadCryptBucket implements Bucket, Serializable {

  /** Magic number identifying AEADCryptBucket serialization format. */
  public static final int MAGIC = 0xb25b32d6;

  /** Per-record encryption overhead (in bytes) for AEAD authentication tags. */
  static final int OVERHEAD = AeadOutputStream.AES_OVERHEAD;

  /** Current serialization format version. */
  static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  /** Valid AES key lengths in bytes (16=128-bit, 24=192-bit, 32=256-bit). */
  private static final Set<Integer> VALID_KEY_LENGTHS = Set.of(16, 24, 32);

  /**
   * @param underlying Backing storage bucket for encrypted data
   * @param key AES key with valid length (16, 24, or 32 bytes)
   * @throws IllegalArgumentException If key length is invalid
   */
  public AeadCryptBucket(Bucket underlying, byte[] key) {
    this.underlying = underlying;
    if (!VALID_KEY_LENGTHS.contains(key.length)) {
      throw new IllegalArgumentException("Invalid key length: " + key.length);
    }
    this.key = Arrays.copyOf(key, key.length);
  }

  /**
   * Deserialization constructor. Reconstructs bucket from stored format.
   *
   * @param dis Data source containing serialized bucket
   * @param masterKey Master secret for decrypting underlying bucket if needed
   * @throws StorageFormatException If version mismatch or invalid key length
   * @throws ResumeFailedException If underlying bucket restoration fails
   * @implNote Reads format: [MAGIC][VERSION][KEY_LEN][KEY][READONLY][UNDERLYING_DATA]. Validates
   *     version and key length before reconstruction.
   */
  public AeadCryptBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws IOException, StorageFormatException, ResumeFailedException {
    // Magic already read by caller.
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Unsupported version: " + version);
    }

    int keyLength = dis.readByte();
    if (!VALID_KEY_LENGTHS.contains(keyLength)) {
      throw new StorageFormatException("Invalid key length: " + keyLength);
    }

    key = new byte[keyLength];
    dis.readFully(key);
    readOnly = dis.readBoolean();
    underlying = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
  }

  /** For serialization proxies only. */
  protected AeadCryptBucket() {
    underlying = null;
    key = null;
  }

  /**
   * {@inheritDoc}
   *
   * @return Buffered stream with AEAD encryption
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    return new BufferedOutputStream(getOutputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * @return Unbuffered AEAD-encrypted output stream
   * @throws IOException If bucket is read-only
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    synchronized (this) {
      if (readOnly) {
        throw new IOException("Read only");
      }
    }
    OutputStream os = underlying.getOutputStreamUnbuffered();
    return AeadOutputStream.createAES(os, key, Global.SECURE_RANDOM);
  }

  /**
   * {@inheritDoc}
   *
   * @return Buffered stream with AEAD decryption
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(getInputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * @return Unbuffered AEAD-decrypted input stream
   */
  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    InputStream is = underlying.getInputStreamUnbuffered();
    return AeadInputStream.createAes(is, key);
  }

  @Override
  public String getName() {
    return "AEADEncrypted:" + underlying.getName();
  }

  /**
   * {@inheritDoc}
   *
   * @return Logical size excluding AEAD overhead bytes
   */
  @Override
  public long size() {
    return underlying.size() - OVERHEAD;
  }

  @Override
  public synchronized boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public synchronized void setReadOnly() {
    readOnly = true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Disposes both this bucket and underlying storage.
   */
  @Override
  public void dispose() {
    underlying.dispose();
  }

  @Override
  public void close() {
    underlying.close();
  }

  /**
   * {@inheritDoc}
   *
   * @return Read-only shadow sharing encryption key and underlying storage's shadow. Modifications
   *     to underlying data will affect both instances.
   */
  @Override
  public Bucket createShadow() {
    Bucket underShadow = underlying.createShadow();
    AeadCryptBucket ret = new AeadCryptBucket(underShadow, key);
    ret.setReadOnly();
    return ret;
  }

  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    underlying.onResume(context);
  }

  /**
   * {@inheritDoc}
   *
   * @implSpec Writes serialization header followed by underlying bucket's data. Structure:
   *     [MAGIC][VERSION][KEY_LEN][KEY][READONLY][UNDERLYING_DATA].
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    dos.writeByte(key.length);
    dos.write(key);
    dos.writeBoolean(readOnly);
    underlying.storeTo(dos);
  }

  /** Backing storage for encrypted data. */
  private final Bucket underlying;

  /** AES encryption key. */
  private final byte[] key;

  /** Read-only state flag. */
  private boolean readOnly;
}
