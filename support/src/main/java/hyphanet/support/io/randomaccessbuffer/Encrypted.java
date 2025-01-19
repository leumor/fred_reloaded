/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.randomaccessbuffer;

import hyphanet.base.Fields;
import hyphanet.crypt.CryptByteBuffer;
import hyphanet.crypt.key.KeyGenUtil;
import hyphanet.crypt.key.KeyType;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.crypt.mac.Mac;
import hyphanet.crypt.mac.MacType;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.StorageFormatException;
import hyphanet.support.io.bucket.BucketTools;
import org.bouncycastle.crypto.SkippingStreamCipher;
import org.bouncycastle.crypto.engines.ChaChaEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe implementation of RandomAccessBuffer that provides encryption using a
 * SkippingStreamCipher. This class encrypts data using ChaCha stream cipher and provides
 * integrity verification through MAC. The encrypted data is stored in an underlying
 * RandomAccessBuffer along with a header containing encryption metadata.
 *
 * <h2>Security Features:</h2>
 * <ul>
 *   <li>Encryption using ChaCha stream cipher (128/256 bit)</li>
 *   <li>Message Authentication Code (MAC) for integrity verification</li>
 *   <li>Secure key derivation from master secret</li>
 *   <li>Thread-safe read/write operations</li>
 * </ul>
 *
 * @author unixninja92
 * @see RandomAccessBuffer
 * @see Serializable
 */
public final class Encrypted implements RandomAccessBuffer, Serializable {

    /**
     * Magic number used to identify encrypted buffer format
     */
    public static final int MAGIC = 0x39ea94c2;

    /**
     * Enumeration of key derivation function inputs used to derive encryption and IV keys from
     * the unencrypted base key.
     */
    public enum KdfInput {
        /**
         * Used for deriving the key that encrypts the underlying RandomAccessBuffer
         */
        UNDERLYING_KEY(),

        /**
         * Used for deriving the initialization vector for underlying RandomAccessBuffer
         * encryption
         */
        UNDERLYING_IV();

        /**
         * String representation of the KDF input type
         */
        public final String input;

        KdfInput() {
            this.input = name();
        }

    }

    /**
     * Defines the encryption algorithms, MAC types, and associated parameters for different
     * encryption configurations.
     */
    public enum Type {
        /**
         * ChaCha encryption with 128-bit key strength
         */
        CHACHA_128(1, 12, CryptByteBuffer.Type.CHACHA_128, MacType.HMAC_SHA_256, 32),

        /**
         * ChaCha encryption with 256-bit key strength
         */
        CHACHA_256(2, 12, CryptByteBuffer.Type.CHACHA_256, MacType.HMAC_SHA_256, 32);

        public final int bitmask;
        public final int headerLen;//bytes
        public final CryptByteBuffer.Type encryptType;
        public final KeyType encryptKey;
        public final MacType macType;
        public final KeyType macKey;
        public final int macLen;//bytes
        private static final Map<Integer, Type> byBitmask = new HashMap<>();

        static {
            for (Type type : values()) {
                byBitmask.put(type.bitmask, type);
            }
        }

        /**
         * Creates the ChaCha enum values.
         *
         * @param bitmask      The version number
         * @param magAndVerLen Length of magic value and version
         * @param type         Alg to use for encrypting the data
         * @param macType      Alg to use for MAC generation
         * @param macLen       The length of the MAC output in bytes
         */
        Type(
            int bitmask,
            int magAndVerLen,
            CryptByteBuffer.Type type,
            MacType macType,
            int macLen
        ) {
            this.bitmask = bitmask;
            this.encryptType = type;
            this.encryptKey = type.keyType;
            this.macType = macType;
            this.macKey = macType.keyType;
            this.macLen = macLen;
            this.headerLen =
                magAndVerLen + (encryptKey.keySize >> 3) + (encryptKey.ivSize >> 3) + macLen;
        }

        public static Type getByBitmask(int val) {
            return byBitmask.get(val);
        }

        /**
         * Returns an instance of the SkippingStreamCipher the goes with the current enum
         * value.
         */
        public final SkippingStreamCipher get() {
            return new ChaChaEngine();
        }

    }

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Magic number used to mark the end of encrypted data
     */
    private static final long END_MAGIC = 0x2c158a6c7772acd3L;

    /**
     * Length of version and magic number fields in bytes
     */
    private static final int VERSION_AND_MAGIC_LENGTH = 12;

    /**
     * Creates an instance of Encrypted Random Access Buffer wrapping an underlying buffer.
     * Keys for key encryption and MAC generation are derived from the MasterSecret. If this is
     * a new Encrypted Random Access Buffer then keys are generated and the footer is written
     * to the end of the underlying buffer. Otherwise, the footer is read from the underlying
     * buffer.
     *
     * @param type       The encryption configuration to use
     * @param underlying The underlying buffer that will be storing the data. Must be larger
     *                   than the footer size specified in type.
     * @param masterKey  The MasterSecret that will be used to derive various keys.
     * @param newBuffer  If true, initializes a new encrypted buffer; if false, reads existing
     *                   header
     *
     * @throws IOException              If I/O errors occur
     * @throws GeneralSecurityException If cryptographic operations fail
     */
    public Encrypted(
        Type type,
        RandomAccessBuffer underlying,
        MasterSecret masterKey,
        boolean newBuffer
    ) throws IOException, GeneralSecurityException {
        this.type = type;
        this.underlyingBuffer = underlying;

        setup(masterKey, newBuffer);
    }

    /**
     * Creates a new encrypted random access buffer from a data stream.
     *
     * @param dis                   The input stream containing serialized buffer data
     * @param fg                    Generator for temporary filenames
     * @param persistentFileTracker Tracker for persistent file resources
     * @param masterKey             The master secret for key derivation
     *
     * @return A new Encrypted instance
     *
     * @throws IOException            If I/O errors occur during reading
     * @throws StorageFormatException If the stored format is invalid
     * @throws ResumeFailedException  If the buffer cannot be resumed
     */
    public static RandomAccessBuffer create(
        DataInputStream dis,
        FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker,
        MasterSecret masterKey
    ) throws IOException, StorageFormatException, ResumeFailedException {
        Type type = Encrypted.Type.getByBitmask(dis.readInt());
        if (type == null) {
            throw new StorageFormatException("Unknown EncryptedRandomAccessBufferType");
        }
        RandomAccessBuffer underlying = BucketTools.restoreRabFrom(
            dis,
            fg,
            persistentFileTracker,
            masterKey
        );
        try {
            return new Encrypted(type, underlying, masterKey, false);
        } catch (GeneralSecurityException e) {
            throw new ResumeFailedException("Crypto error resuming", e);
        }
    }

    /**
     * Returns the size of the encrypted data, excluding the header length.
     *
     * @return The size in bytes of the encrypted data
     */
    @Override
    public long size() {
        return underlyingBuffer.size() - type.headerLen;
    }

    /**
     * Reads and decrypts data from the buffer in a thread-safe manner. The operation uses the
     * read cipher instance with proper position tracking.
     *
     * @param fileOffset Starting position in the buffer to read from
     * @param buf        Destination array for decrypted data
     * @param bufOffset  Starting offset in the destination array
     * @param length     Number of bytes to read
     *
     * @throws IOException              If the buffer is closed or read operation fails
     * @throws IllegalArgumentException If fileOffset is negative
     */
    @Override
    public void pread(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        if (isClosed) {
            throw new IOException(
                "This RandomAccessBuffer has already been closed. It can no longer" +
                " be read from.");
        }

        if (fileOffset < 0) {
            throw new IllegalArgumentException("Cannot read before zero");
        }
        if (fileOffset + length > size()) {
            throw new IOException(
                "Cannot read after end: trying to read from " + fileOffset + " to " +
                (fileOffset + length) + " on block length " + size());
        }

        byte[] cipherText = new byte[length];
        underlyingBuffer.pread(fileOffset + type.headerLen, cipherText, 0, length);

        readLock.lock();
        try {
            //cipherRead.seekTo(fileOffset);
            // seekTo() does reset() and then skip(). So it always skips from 0.
            // This is ridiculously slow for big temp files.
            // FIXME REVIEW CRYPTO: Is this safe? It should be, we're using the published
            //  skip() API...
            long position = cipherRead.getPosition();
            long delta = fileOffset - position;
            cipherRead.skip(delta);
            cipherRead.processBytes(cipherText, 0, length, buf, bufOffset);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Encrypts and writes data to the buffer in a thread-safe manner. The operation uses the
     * write cipher instance with proper position tracking.
     *
     * @param fileOffset Starting position in the buffer to write to
     * @param buf        Source array containing data to encrypt
     * @param bufOffset  Starting offset in the source array
     * @param length     Number of bytes to write
     *
     * @throws IOException              If the buffer is closed or write operation fails
     * @throws IllegalArgumentException If fileOffset is negative
     */
    @Override
    public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        if (isClosed) {
            throw new IOException(
                "This RandomAccessBuffer has already been closed. It can no longer" +
                " be written to.");
        }

        if (fileOffset < 0) {
            throw new IllegalArgumentException("Cannot read before zero");
        }
        if (fileOffset + length > size()) {
            throw new IOException(
                "Cannot write after end: trying to write from " + fileOffset + " to " +
                (fileOffset + length) + " on block length " + size());
        }

        byte[] cipherText = new byte[length];

        writeLock.lock();
        try {
            //cipherWrite.seekTo(fileOffset)
            // seekTo() does reset() and then skip(). So it always skips from 0.
            // This is ridiculously slow for big temp files.
            // FIXME REVIEW CRYPTO: Is this safe? It should be, we're using the published
            //  skip() API...
            long position = cipherWrite.getPosition();
            long delta = fileOffset - position;
            cipherWrite.skip(delta);
            cipherWrite.processBytes(buf, bufOffset, length, cipherText, 0);
        } finally {
            writeLock.unlock();
        }
        underlyingBuffer.pwrite(fileOffset + type.headerLen, cipherText, 0, length);
    }

    /**
     * Closes the encrypted buffer and its underlying storage. Once closed, no further read or
     * write operations are allowed.
     */
    @Override
    public void close() {
        if (!isClosed) {
            isClosed = true;
            underlyingBuffer.close();
        }
    }

    /**
     * Disposes of the encrypted buffer and its resources. Calls close() and disposes of the
     * underlying buffer.
     */
    @Override
    public void dispose() {
        close();
        underlyingBuffer.dispose();
    }

    /**
     * Acquires a lock on the underlying buffer to prevent premature closure.
     *
     * @return A lock object representing the acquired lock
     *
     * @throws IOException If the lock cannot be acquired
     */
    @Override
    public RabLock lockOpen() throws IOException {
        return underlyingBuffer.lockOpen();
    }

    /**
     * Handles resumption of the encrypted buffer after deserialization. Reinitializes
     * cryptographic components using the provided master secret.
     *
     * @param context Context containing resumption state and master secret
     *
     * @throws ResumeFailedException If cryptographic setup fails
     */
    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        underlyingBuffer.onResume(context);
        try {
            setup(context.getPersistentMasterSecret(), false);
        } catch (IOException e) {
            throw new ResumeFailedException("Disk I/O error resuming", e);
        } catch (GeneralSecurityException e) {
            throw new ResumeFailedException(
                "Impossible security error resuming - maybe we " + "lost a codec?",
                                            e
            );
        }
    }

    /**
     * Serializes the encrypted buffer state. Writes the magic number, type bitmask, and
     * underlying buffer data.
     *
     * @param dos Output stream for serialization
     *
     * @throws IOException If serialization fails
     */
    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(type.bitmask);
        underlyingBuffer.storeTo(dos);
    }

    @Override
    public int hashCode() {
        return 31 * (31 + type.hashCode()) + underlyingBuffer.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Encrypted other)) {
            return false;
        }
        return type == other.type && underlyingBuffer.equals(other.underlyingBuffer);
    }

    /**
     * Initializes or resumes the encrypted buffer's cryptographic components. Sets up cipher
     * instances, derives encryption keys, and verifies or creates the header.
     *
     * @param masterKey The master secret used for key derivation
     * @param newFile   If true, initializes a new buffer; if false, reads existing header
     *
     * @throws IOException              If I/O operations fail
     * @throws GeneralSecurityException If cryptographic operations fail
     */
    private void setup(MasterSecret masterKey, boolean newFile)
        throws IOException, GeneralSecurityException {
        this.cipherRead = this.type.get();
        this.cipherWrite = this.type.get();

        this.headerEncKey = masterKey.deriveKey(type.encryptKey);

        this.headerMacKey = masterKey.deriveKey(type.macKey);


        if (underlyingBuffer.size() < type.headerLen) {
            throw new IOException(
                "Underlying RandomAccessBuffer is not long enough to include the " +
                "footer.");
        }

        byte[] header = new byte[VERSION_AND_MAGIC_LENGTH];
        int offset = 0;
        underlyingBuffer.pread(
            type.headerLen - VERSION_AND_MAGIC_LENGTH,
            header,
            offset,
            VERSION_AND_MAGIC_LENGTH
        );

        int readVersion = ByteBuffer.wrap(header, offset, 4).getInt();
        offset += 4;
        long magic = ByteBuffer.wrap(header, offset, 8).getLong();

        if (!newFile && END_MAGIC != magic) {
            throw new IOException("This is not an EncryptedRandomAccessBuffer!");
        }

        version = type.bitmask;
        if (newFile) {
            this.headerEncIV = KeyGenUtil.genIV(type.encryptType.ivSize).getIV();
            this.unencryptedBaseKey = KeyGenUtil.genSecretKey(type.encryptKey);
            writeHeader();
        } else {
            if (readVersion != version) {
                throw new IOException("Version of the underlying RandomAccessBuffer is " +
                                      "incompatible with this ERATType");
            }

            if (!verifyHeader()) {
                throw new GeneralSecurityException("MAC is incorrect");
            }
        }
        ParametersWithIV tempPram;
        try {
            KeyParameter cipherKey =
                new KeyParameter(KeyGenUtil.deriveSecretKey(unencryptedBaseKey,
                                                                                 getClass(),
                                                                                 KdfInput.UNDERLYING_KEY.input,
                                                                                 type.encryptKey
                                                                )
                                                                .getEncoded());
            tempPram = new ParametersWithIV(
                cipherKey,
                KeyGenUtil.deriveIvParameterSpec(
                              unencryptedBaseKey,
                              getClass(),
                              KdfInput.UNDERLYING_IV.input,
                              type.encryptKey
                          )
                          .getIV()
            );
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e); // Must be a bug.
        }
        //includes key
        ParametersWithIV cipherParams = tempPram;
        assert cipherRead != null;
        cipherRead.init(false, cipherParams);
        assert cipherWrite != null;
        cipherWrite.init(true, cipherParams);
    }

    /**
     * Writes the encryption header to the underlying buffer. The header contains:
     * <ul>
     *   <li>Initialization vector</li>
     *   <li>Encrypted base key</li>
     *   <li>MAC value</li>
     *   <li>Version information</li>
     *   <li>Magic number</li>
     * </ul>
     *
     * @throws IOException              If writing to the underlying buffer fails
     * @throws GeneralSecurityException If encryption operations fail
     */
    private void writeHeader() throws IOException, GeneralSecurityException {
        if (isClosed) {
            throw new IOException(
                "This RandomAccessBuffer has already been closed. This should not" +
                " happen.");
        }
        byte[] header = new byte[type.headerLen];
        int offset = 0;

        int ivLen = headerEncIV.length;
        System.arraycopy(headerEncIV, 0, header, offset, ivLen);
        offset += ivLen;

        byte[] encryptedKey;
        try {
            CryptByteBuffer crypt = new CryptByteBuffer(
                type.encryptType,
                                                        headerEncKey,
                                                        headerEncIV
            );
            encryptedKey = crypt.encryptCopy(unencryptedBaseKey.getEncoded());
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new GeneralSecurityException(
                "Something went wrong with key generation. " + "please " + "report",
                                               e
            );
        }
        System.arraycopy(encryptedKey, 0, header, offset, encryptedKey.length);
        offset += encryptedKey.length;

        byte[] ver = ByteBuffer.allocate(4).putInt(version).array();
        try {
            Mac mac = new Mac(type.macType, headerMacKey);
            byte[] macResult = Fields.copyToArray(mac.genMac(
                headerEncIV,
                unencryptedBaseKey.getEncoded(),
                ver
            ));
            System.arraycopy(macResult, 0, header, offset, macResult.length);
            offset += macResult.length;
        } catch (InvalidKeyException e) {
            throw new GeneralSecurityException(
                "Something went wrong with key generation. " + "please " + "report",
                                               e
            );
        }

        System.arraycopy(ver, 0, header, offset, ver.length);
        offset += ver.length;

        byte[] magic = ByteBuffer.allocate(8).putLong(END_MAGIC).array();
        System.arraycopy(magic, 0, header, offset, magic.length);

        underlyingBuffer.pwrite(0, header, 0, header.length);
    }

    /**
     * Verifies the integrity of the encryption header. Reads the IV, encrypted key, and MAC
     * from the header, then decrypts the key and verifies the MAC value.
     *
     * @return true if the MAC verification succeeds, false otherwise
     *
     * @throws IOException         If reading from the underlying buffer fails
     * @throws InvalidKeyException If the decryption key is invalid
     */
    private boolean verifyHeader() throws IOException, InvalidKeyException {
        if (isClosed) {
            throw new IOException(
                "This RandomAccessBuffer has already been closed. This should not" +
                " happen.");
        }
        byte[] footer = new byte[type.headerLen - VERSION_AND_MAGIC_LENGTH];
        int offset = 0;
        underlyingBuffer.pread(0, footer, offset, type.headerLen - VERSION_AND_MAGIC_LENGTH);

        headerEncIV = new byte[type.encryptType.ivSize];
        System.arraycopy(footer, offset, headerEncIV, 0, headerEncIV.length);
        offset += headerEncIV.length;

        int keySize = type.encryptKey.keySize >> 3;
        byte[] encryptedKey = new byte[keySize];
        System.arraycopy(footer, offset, encryptedKey, 0, keySize);
        offset += keySize;
        try {
            CryptByteBuffer crypt = new CryptByteBuffer(
                type.encryptType,
                                                        headerEncKey,
                                                        headerEncIV
            );
            unencryptedBaseKey = KeyGenUtil.getSecretKey(
                type.encryptKey,
                crypt.decryptCopy(encryptedKey)
            );
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IOException("Error reading encryption keys from header.");
        }

        byte[] mac = new byte[type.macLen];
        System.arraycopy(footer, offset, mac, 0, type.macLen);

        byte[] ver = ByteBuffer.allocate(4).putInt(version).array();
        Mac authCode = new Mac(type.macType, headerMacKey);
        return authCode.verifyData(mac, headerEncIV, unencryptedBaseKey.getEncoded(), ver);
    }

    /**
     * Lock for synchronizing read operations
     */
    private final ReentrantLock readLock = new ReentrantLock();

    /**
     * Lock for synchronizing write operations
     */
    private final ReentrantLock writeLock = new ReentrantLock();

    /**
     * Encryption configuration type
     */
    private final Type type;

    /**
     * Underlying storage buffer
     */
    private final RandomAccessBuffer underlyingBuffer;

    /**
     * Cipher instance for read operations
     */
    private transient SkippingStreamCipher cipherRead;

    /**
     * Cipher instance for write operations
     */
    private transient SkippingStreamCipher cipherWrite;

    /**
     * Key used for MAC generation
     */
    private transient SecretKey headerMacKey;

    /**
     * Flag indicating if the buffer is closed
     */
    private transient volatile boolean isClosed = false;

    /**
     * Base key before encryption
     */
    private transient SecretKey unencryptedBaseKey;

    /**
     * Key used for header encryption
     */
    private transient SecretKey headerEncKey;

    /**
     * Initialization vector for header encryption
     */
    private transient byte[] headerEncIV;

    /**
     * Version number of the encryption format
     */
    private int version;
}
