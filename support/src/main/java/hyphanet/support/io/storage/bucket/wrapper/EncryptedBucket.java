package hyphanet.support.io.storage.bucket.wrapper;

import com.uber.nullaway.annotations.RequiresNonNull;
import hyphanet.base.Fields;
import hyphanet.crypt.CryptByteBuffer;
import hyphanet.crypt.key.KeyGenUtil;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.crypt.mac.Mac;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.EncryptType;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.BucketTools;
import hyphanet.support.io.storage.bucket.RandomAccessBucket;
import hyphanet.support.io.storage.rab.EncryptedRab;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.stream.NullInputStream;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import org.bouncycastle.crypto.SkippingStreamCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A Bucket implementation that provides encryption capabilities using the same format as {@link
 * EncryptedRab} Random Access Buffer. This allows for seamless conversion between encrypted bucket
 * and buffer formats without data replication.
 *
 * <h2>Encryption Details</h2>
 *
 * The encryption process involves:
 *
 * <ul>
 *   <li>Header encryption using a master key
 *   <li>Content encryption using derived keys
 *   <li>MAC-based integrity verification
 * </ul>
 *
 * <h2>Security Features</h2>
 *
 * <ul>
 *   <li>Secure key derivation from master secret
 *   <li>Integrity protection using MAC
 *   <li>IV-based encryption for enhanced security
 * </ul>
 *
 * @author toad
 * @see RandomAccessBucket
 * @see java.io.Serializable
 */
public class EncryptedBucket extends AbstractStorage implements RandomAccessBucket, Serializable {

  /** Magic number used for format validation */
  public static final int MAGIC = 0xd8ba4c7e;

  @Serial private static final long serialVersionUID = 1L;

  /** Magic number used to verify the end of encrypted data */
  private static final long END_MAGIC = 0x2c158a6c7772acd3L;

  /** Combined length of version information and magic numbers */
  private static final int VERSION_AND_MAGIC_LENGTH = 12;

  /**
   * Constructs an encrypted bucket with specified encryption type and underlying storage.
   *
   * @param type The encryption type to be used
   * @param underlying The underlying storage implementation
   * @param masterKey The master key for encryption
   */
  public EncryptedBucket(EncryptType type, RandomAccessBucket underlying, MasterSecret masterKey) {
    this.type = type;
    this.underlying = underlying;
    baseSetup(masterKey);
  }

  /**
   * Reconstructs an encrypted bucket from a data stream.
   *
   * @param dis The input stream containing bucket data
   * @param fg Generator for temporary filenames
   * @param persistentFileTracker Tracks persistent files
   * @param masterKey Master key for decryption
   * @throws IOException If I/O errors occur
   * @throws ResumeFailedException If bucket reconstruction fails
   * @throws StorageFormatException If the stored format is invalid
   */
  public EncryptedBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws IOException, ResumeFailedException, StorageFormatException {
    var encryptType = EncryptType.getByBitmask(dis.readInt());
    if (encryptType == null) {
      throw new ResumeFailedException("Unknown EncryptedRandomAccessBucket type");
    }
    type = encryptType;
    underlying =
        (RandomAccessBucket) BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
    this.baseSetup(masterKey);
  }

  /**
   * Creates an unbuffered output stream for writing encrypted data to the underlying storage.
   *
   * <h2>Encryption Process</h2>
   *
   * The stream:
   *
   * <ul>
   *   <li>Writes the encrypted header containing IV and key information
   *   <li>Encrypts data using the configured cipher before writing
   *   <li>Maintains data integrity through MAC verification
   * </ul>
   *
   * @return An encrypted output stream for writing data
   * @throws IOException If an I/O error occurs or if the bucket has been disposed
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    if (disposed()) {
      throw new IOException(
          "This RandomAccessBuffer has already been closed. This should not" + " happen.");
    }
    OutputStream uos = underlying.getOutputStreamUnbuffered();
    try {
      return new MyOutputStream(uos, setup(uos));
    } catch (GeneralSecurityException e) {
      throw new IOException("Unable to create encrypted bucket", e);
    }
  }

  /**
   * Creates an unbuffered input stream for reading encrypted data from the underlying storage.
   *
   * <h2>Decryption Process</h2>
   *
   * The stream:
   *
   * <ul>
   *   <li>Verifies the header MAC and format
   *   <li>Extracts encryption parameters
   *   <li>Decrypts data using the configured cipher while reading
   * </ul>
   *
   * @return A decrypting input stream, or {@link NullInputStream} if the bucket is empty
   * @throws IOException If an I/O error occurs or if the bucket has been disposed
   */
  @Override
  public @NonNull InputStream getInputStreamUnbuffered() throws IOException {
    if (size() == 0) {
      return new NullInputStream();
    }
    if (disposed()) {
      throw new IOException(
          "This RandomAccessBuffer has already been closed. This should not" + " happen.");
    }
    InputStream is = underlying.getInputStreamUnbuffered();
    try {
      return new MyInputStream(is, setup(is));
    } catch (GeneralSecurityException e) {
      throw new IOException("Unable to read encrypted bucket", e);
    }
  }

  /**
   * Returns a descriptive name for this encrypted bucket. The name includes the class name and the
   * underlying bucket's name.
   *
   * @return A string containing class name and underlying bucket name
   */
  @Override
  public String getName() {
    return getClass().getName() + ":" + underlying.getName();
  }

  /**
   * Calculates the actual data size excluding the encryption header.
   *
   * @return The size of the encrypted data in bytes, excluding header
   */
  @Override
  public long size() {
    long size = underlying.size();
    if (size == 0) {
      return 0;
    }
    return size - type.headerLen;
  }

  /**
   * Checks if this encrypted bucket is read-only.
   *
   * @return true if the underlying bucket is read-only
   */
  @Override
  public boolean isReadOnly() {
    return underlying.isReadOnly();
  }

  /** Makes this encrypted bucket read-only by making the underlying storage read-only. */
  @Override
  public void setReadOnly() {
    underlying.setReadOnly();
  }

  @Override
  public void close() {
    if (!setClosed()) {
      return;
    }
    underlying.close();
  }

  @Override
  public void dispose() {
    if (!setDisposed()) {
      return;
    }
    underlying.dispose();
  }

  /**
   * Creates a read-only shadow copy of this encrypted bucket. The copy shares the same underlying
   * storage but has its own encryption state.
   *
   * @return A new encrypted bucket instance using the same underlying storage
   */
  @Override
  public RandomAccessBucket createShadow() {
    RandomAccessBucket copy = underlying.createShadow();
    return new EncryptedBucket(type, copy, masterKey);
  }

  /**
   * Converts this encrypted bucket to a RandomAccessBuffer. After conversion, the bucket becomes
   * read-only.
   *
   * @return An encrypted random access buffer containing the bucket's data
   * @throws IOException If conversion fails or the bucket is empty
   */
  @Override
  public Rab toRandomAccessBuffer() throws IOException {
    if (underlying.size() < type.headerLen) {
      throw new IOException("Converting empty bucket");
    }
    underlying.setReadOnly();
    Rab r = underlying.toRandomAccessBuffer();
    try {
      return new EncryptedRab(type, r, masterKey, false);
    } catch (GeneralSecurityException e) {
      throw new IOException("Unable to convert encrypted bucket", e);
    }
  }

  /**
   * Creates a buffered output stream for writing encrypted data.
   *
   * @return A buffered output stream wrapping the unbuffered stream
   * @throws IOException If stream creation fails
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    return new BufferedOutputStream(getOutputStreamUnbuffered());
  }

  /**
   * Creates a buffered input stream for reading decrypted data.
   *
   * @return A buffered input stream wrapping the unbuffered stream
   * @throws IOException If stream creation fails
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(getInputStreamUnbuffered());
  }

  /**
   * Handles bucket resumption after restart. Updates the master key and reinitializes encryption
   * parameters.
   *
   * @param context The resume context containing the master secret
   * @throws ResumeFailedException If resumption fails
   */
  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    underlying.onResume(context);
    this.masterKey = context.getPersistentMasterSecret();
    baseSetup(masterKey);
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(type.bitmask);
    underlying.storeTo(dos);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + type.hashCode();
    result = prime * result + underlying.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof EncryptedBucket other)) {
      return false;
    }
    return type == other.type && underlying.equals(other.underlying);
  }

  /**
   * Retrieves the underlying storage implementation.
   *
   * @return The underlying RandomAccessible instance
   */
  public RandomAccessBucket getUnderlying() {
    return underlying;
  }

  /**
   * Initializes base encryption components using the master key. Sets up header encryption key, MAC
   * key, and version information.
   *
   * @param masterKey The master secret for deriving encryption keys
   */
  private void baseSetup(MasterSecret masterKey) {
    this.masterKey = masterKey;

    this.headerEncKey = masterKey.deriveKey(type.encryptKey);
    this.headerMacKey = masterKey.deriveKey(type.macKey);

    version = type.bitmask;
  }

  /**
   * Configures encryption cipher for output operations. Generates IVs, writes header, and
   * initializes encryption parameters.
   *
   * @param os The output stream to write the header to
   * @return Configured cipher for encryption
   * @throws GeneralSecurityException If crypto operations fail
   * @throws IOException If writing header fails
   */
  private SkippingStreamCipher setup(OutputStream os) throws GeneralSecurityException, IOException {
    this.headerEncIV = KeyGenUtil.genIV(type.encryptType.ivSize).getIV();
    this.unencryptedBaseKey = KeyGenUtil.genSecretKey(type.encryptKey);
    writeHeader(os);
    setupKeys();
    SkippingStreamCipher cipherWrite = this.type.get();
    cipherWrite.init(true, cipherParams);
    return cipherWrite;
  }

  /**
   * Writes the encryption header containing IV, encrypted key, MAC, and magic numbers.
   *
   * @param os The output stream to write the header to
   * @throws GeneralSecurityException If encryption operations fail
   * @throws IOException If writing fails
   */
  @RequiresNonNull({"unencryptedBaseKey", "headerEncIV"})
  private void writeHeader(OutputStream os) throws GeneralSecurityException, IOException {
    byte[] header = new byte[type.headerLen];
    int offset = 0;

    assert headerEncIV != null;
    int ivLen = headerEncIV.length;
    System.arraycopy(headerEncIV, 0, header, offset, ivLen);
    offset += ivLen;

    byte[] encryptedKey;
    try {
      CryptByteBuffer crypt = new CryptByteBuffer(type.encryptType, headerEncKey, headerEncIV);
      encryptedKey = crypt.encryptCopy(unencryptedBaseKey.getEncoded());
    } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
      throw new GeneralSecurityException(
          "Something went wrong with key generation. " + "Please report", e);
    }
    System.arraycopy(encryptedKey, 0, header, offset, encryptedKey.length);
    offset += encryptedKey.length;

    byte[] ver = ByteBuffer.allocate(4).putInt(version).array();
    try {
      Mac mac = new Mac(type.macType, headerMacKey);
      byte[] macResult =
          Fields.copyToArray(mac.genMac(headerEncIV, unencryptedBaseKey.getEncoded(), ver));
      System.arraycopy(macResult, 0, header, offset, macResult.length);
      offset += macResult.length;
    } catch (InvalidKeyException e) {
      throw new GeneralSecurityException(
          "Something went wrong with key generation. " + "Please report", e);
    }

    System.arraycopy(ver, 0, header, offset, ver.length);
    offset += ver.length;

    byte[] magic = ByteBuffer.allocate(8).putLong(END_MAGIC).array();
    System.arraycopy(magic, 0, header, offset, magic.length);

    os.write(header);
  }

  /**
   * Configures decryption cipher for input operations. Reads and validates header, verifies MAC,
   * and initializes decryption parameters.
   *
   * @param is The input stream to read the header from
   * @return Configured cipher for decryption
   * @throws IOException If reading header fails
   * @throws GeneralSecurityException If crypto operations fail
   */
  private SkippingStreamCipher setup(InputStream is) throws IOException, GeneralSecurityException {
    byte[] fullHeader = new byte[type.headerLen];
    try (DataInputStream dis = new DataInputStream(is)) {
      dis.readFully(fullHeader);
    } catch (EOFException e) {
      throw new IOException(
          "Underlying RandomAccessBuffer is not long enough to include the " + "footer.");
    }
    byte[] header =
        Arrays.copyOfRange(
            fullHeader, fullHeader.length - VERSION_AND_MAGIC_LENGTH, fullHeader.length);
    int offset = 0;
    int readVersion = ByteBuffer.wrap(header, offset, 4).getInt();
    offset += 4;
    long magic = ByteBuffer.wrap(header, offset, 8).getLong();
    if (END_MAGIC != magic) {
      throw new IOException("This is not an EncryptedRandomAccessBuffer!");
    }
    if (readVersion != version) {
      throw new IOException(
          "Version of the underlying RandomAccessBuffer is " + "incompatible with this ERATType");
    }
    if (!verifyHeader(fullHeader)) {
      throw new GeneralSecurityException("MAC is incorrect");
    }
    setupKeys();
    SkippingStreamCipher cipherRead = this.type.get();
    cipherRead.init(false, cipherParams);
    return cipherRead;
  }

  /**
   * Verifies the integrity of the encryption header using MAC.
   *
   * @param fullHeader The complete header data including IV, key, and MAC
   * @return true if MAC verification succeeds
   * @throws IOException If header parsing fails
   * @throws InvalidKeyException If key operations fail
   */
  private boolean verifyHeader(byte[] fullHeader) throws IOException, InvalidKeyException {
    byte[] footer = Arrays.copyOfRange(fullHeader, 0, fullHeader.length - VERSION_AND_MAGIC_LENGTH);
    int offset = 0;

    headerEncIV = new byte[type.encryptType.ivSize];
    System.arraycopy(footer, offset, headerEncIV, 0, headerEncIV.length);
    offset += headerEncIV.length;

    int keySize = type.encryptKey.keySize >> 3;
    byte[] encryptedKey = new byte[keySize];
    System.arraycopy(footer, offset, encryptedKey, 0, keySize);
    offset += keySize;
    try {
      CryptByteBuffer crypt = new CryptByteBuffer(type.encryptType, headerEncKey, headerEncIV);
      unencryptedBaseKey =
          KeyGenUtil.getSecretKey(type.encryptKey, crypt.decryptCopy(encryptedKey));
    } catch (InvalidAlgorithmParameterException e) {
      throw new IOException("Error reading encryption keys from header.");
    }

    byte[] mac = new byte[type.macLen];
    System.arraycopy(footer, offset, mac, 0, type.macLen);

    byte[] ver = ByteBuffer.allocate(4).putInt(version).array();
    Mac authCode = new Mac(type.macType, headerMacKey);
    return authCode.verifyData(mac, headerEncIV, unencryptedBaseKey.getEncoded(), ver);
  }

  /**
   * Initializes encryption parameters using derived keys. Sets up cipher parameters including key
   * and IV for data encryption.
   */
  private void setupKeys() {
    ParametersWithIV tempPram;
    try {
      KeyParameter cipherKey =
          new KeyParameter(
              KeyGenUtil.deriveSecretKey(
                      unencryptedBaseKey,
                      EncryptedRab.class,
                      EncryptedRab.KdfInput.UNDERLYING_KEY.input,
                      type.encryptKey)
                  .getEncoded());
      tempPram =
          new ParametersWithIV(
              cipherKey,
              KeyGenUtil.deriveIvParameterSpec(
                      unencryptedBaseKey,
                      EncryptedRab.class,
                      EncryptedRab.KdfInput.UNDERLYING_IV.input,
                      type.encryptKey)
                  .getIV());
    } catch (InvalidKeyException e) {
      throw new IllegalStateException(e); // Must be a bug.
    }
    this.cipherParams = tempPram;
  }

  /**
   * A specialized output stream that encrypts data before writing to the underlying stream.
   * Processes data through a cipher before writing to maintain data confidentiality.
   */
  static class MyOutputStream extends FilterOutputStream {

    /**
     * Creates a new encrypting output stream.
     *
     * @param out The underlying output stream
     * @param cipher The cipher for encryption
     */
    public MyOutputStream(OutputStream out, SkippingStreamCipher cipher) {
      super(out);
      this.cipherWrite = cipher;
    }

    /**
     * Encrypts and writes a single byte.
     *
     * @param x The byte to write
     * @throws IOException If writing fails
     */
    @Override
    public void write(int x) throws IOException {
      one[0] = (byte) x;
      write(one);
    }

    /**
     * Encrypts and writes an array of bytes.
     *
     * @param buf The buffer containing data to write
     * @throws IOException If writing fails
     */
    @Override
    public void write(byte[] buf) throws IOException {
      write(buf, 0, buf.length);
    }

    /**
     * Encrypts and writes a portion of a byte array.
     *
     * @param buf The buffer containing data to write
     * @param offset The start position in the buffer
     * @param length The number of bytes to write
     * @throws IOException If writing fails
     */
    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
      byte[] ciphertext = new byte[length];
      cipherWrite.processBytes(buf, offset, length, ciphertext, 0);
      out.write(ciphertext);
    }

    /** The cipher used for encryption */
    private final SkippingStreamCipher cipherWrite;

    /** Single byte buffer for write operations */
    private final byte[] one = new byte[1];
  }

  /**
   * A specialized input stream that decrypts data after reading from the underlying stream.
   * Processes data through a cipher after reading to restore original content.
   */
  static class MyInputStream extends FilterInputStream {

    /**
     * Creates a new decrypting input stream.
     *
     * @param in The underlying input stream
     * @param cipher The cipher for decryption
     */
    public MyInputStream(InputStream in, SkippingStreamCipher cipher) {
      super(in);
      this.cipherRead = cipher;
    }

    /**
     * Reads and decrypts a single byte.
     *
     * @return The decrypted byte, or -1 if end of stream
     * @throws IOException If reading fails
     */
    @Override
    public int read() throws IOException {
      int readBytes = read(one);
      if (readBytes <= 0) {
        return readBytes;
      }
      return one[0] & 0xFF;
    }

    /**
     * Reads and decrypts bytes into an array.
     *
     * @param buf The buffer to read into
     * @return The number of bytes read, or -1 if end of stream
     * @throws IOException If reading fails
     */
    @Override
    public int read(byte[] buf) throws IOException {
      return read(buf, 0, buf.length);
    }

    /**
     * Reads and decrypts bytes into a portion of an array.
     *
     * @param buf The buffer to read into
     * @param offset The start position in the buffer
     * @param length The maximum number of bytes to read
     * @return The number of bytes read, or -1 if end of stream
     * @throws IOException If reading fails
     */
    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
      int readBytes = in.read(buf, offset, length);
      if (readBytes <= 0) {
        return readBytes;
      }
      cipherRead.processBytes(buf, offset, readBytes, buf, offset);
      return readBytes;
    }

    /** The cipher used for decryption */
    private final SkippingStreamCipher cipherRead;

    /** Single byte buffer for read operations */
    private final byte[] one = new byte[1];
  }

  /** The encryption type configuration */
  private final EncryptType type;

  /** The underlying storage implementation */
  private final RandomAccessBucket underlying;

  /** Parameters for cipher operations including key and IV */
  private transient @Nullable ParametersWithIV cipherParams; // includes key

  /** Key used for MAC operations on the header */
  private transient SecretKey headerMacKey;

  /** The unencrypted base key used for derivation */
  private transient @Nullable SecretKey unencryptedBaseKey;

  /** Key used for header encryption */
  private transient SecretKey headerEncKey;

  /** Initialization vector for header encryption */
  private transient byte @Nullable [] headerEncIV;

  /** Version identifier for format compatibility */
  private int version;

  /** Master secret for key derivation */
  private transient MasterSecret masterKey;
}
