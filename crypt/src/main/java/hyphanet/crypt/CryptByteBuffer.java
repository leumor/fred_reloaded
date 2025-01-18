/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import hyphanet.base.Fields;
import hyphanet.crypt.key.KeyGenUtil;
import hyphanet.crypt.key.KeyType;
import org.jspecify.annotations.Nullable;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A cryptographic utility class that provides encryption and decryption capabilities for byte
 * arrays and BitSets using specified algorithms, keys, and initialization vectors (IVs).
 * <p>
 * Recommended algorithm: {@link Type#CHACHA_128}
 * </p>
 *
 * @author unixninja92
 * @see Type
 */
public final class CryptByteBuffer implements Serializable {

    /**
     * Represents the cryptographic algorithm configurations available for byte buffer
     * encryption in Hyphanet. This enum defines various symmetric cipher algorithms with their
     * respective properties including block sizes, initialization vector (IV) requirements,
     * and key specifications.
     * <p>
     * Each enum constant provides a complete configuration for a specific encryption
     * algorithm, enabling consistent and secure cryptographic operations throughout the
     * system.
     * </p>
     *
     * @author unixninja92
     * @see CryptByteBuffer
     */
    public enum Type implements Serializable {
        /**
         * Legacy RIJNDAEL implementation using PCFB mode.
         *
         * @deprecated Use {@link #AES_CTR} or {@link #CHACHA_128} instead for better security
         */
        @Deprecated RIJNDAEL_PCFB(8, 32, "RIJNDAEL256/CFB/NoPadding", KeyType.RIJNDAEL_256),

        /**
         * AES cipher in Counter (CTR) mode with 256-bit key strength. Provides strong
         * encryption with parallel processing capabilities.
         */
        AES_CTR(16, 16, "AES/CTR/NOPADDING", KeyType.AES_256),

        /**
         * ChaCha stream cipher with 128-bit security strength. Offers high-speed encryption on
         * software implementations.
         */
        CHACHA_128(32, 8, "CHACHA", KeyType.CHACHA_128),

        /**
         * ChaCha stream cipher with 256-bit security strength. Provides extended security
         * margin compared to CHACHA_128.
         */
        CHACHA_256(64, 8, "CHACHA", KeyType.CHACHA_256);

        /**
         * Bitmask used for algorithm aggregation and identification. This value must be
         * positive and unique for each algorithm type.
         */
        public final int bitmask;

        /**
         * The cipher's block size in bytes. For stream ciphers, this represents the internal
         * state size.
         */
        public final int blockSize;

        /**
         * The size of the initialization vector (IV) in bytes. This value is required for all
         * supported cipher modes.
         */
        public final Integer ivSize; // in bytes

        /**
         * The standardized algorithm name as recognized by Java's cryptography providers. This
         * name includes the cipher, mode, and padding scheme.
         */
        public final String algName;

        /**
         * The base cipher name without mode and padding specifications.
         */
        public final String cipherName;

        /**
         * The key type specification for this cipher. Defines the key generation parameters
         * and constraints.
         */
        public final KeyType keyType;

        /**
         * Constructs a new cipher type configuration with the specified parameters.
         *
         * @param bitmask The unique identifier bitmask for this algorithm
         * @param ivSize  The size of the initialization vector in bytes
         * @param algName The Java provider's algorithm name specification
         * @param keyType The key type specification for this cipher
         *
         * @throws IllegalArgumentException if bitmask is not positive, ivSize is not positive,
         *                                  or algName is blank
         */
        Type(int bitmask, int ivSize, String algName, KeyType keyType) {

            if (bitmask <= 0) {
                throw new IllegalArgumentException("Bitmask must be positive");
            }
            if (ivSize <= 0) {
                throw new IllegalArgumentException("IV size must be positive");
            }
            if (algName.isBlank()) {
                throw new IllegalArgumentException("Algorithm name cannot be blank");
            }

            this.bitmask = bitmask;
            this.ivSize = ivSize;
            this.cipherName = keyType.algName;
            this.blockSize = keyType.keySize;
            this.algName = algName;
            this.keyType = keyType;
        }

    }

    @Serial
    private static final long serialVersionUID = 6143338995971755362L;

    /**
     * Creates a CryptByteBuffer instance with specified algorithm type, key, and optional IV.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The secret key for cryptographic operations
     * @param iv   The initialization vector, or null to generate a random one
     *
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     * @throws InvalidKeyException                if the key is invalid
     */
    public CryptByteBuffer(Type type, SecretKey key, @Nullable IvParameterSpec iv)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this.type = type;
        this.key = key;
        try {
            encryptCipher = Cipher.getInstance(type.algName);
            decryptCipher = Cipher.getInstance(type.algName);

            if (iv != null) {
                this.iv = iv;
                initializeCiphers();
            } else {
                genIV();
            }

        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new IllegalStateException(
                "Cipher initialization failed",
                                            e
            ); // Should be impossible as we bundle BC
        }
    }

    /**
     * Creates a CryptByteBuffer instance with specified algorithm type and key. Generates a
     * random IV if required by the algorithm.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The secret key for cryptographic operations
     *
     * @throws GeneralSecurityException if any cryptographic error occurs
     */
    public CryptByteBuffer(Type type, SecretKey key) throws GeneralSecurityException {
        this(type, key, (IvParameterSpec) null);
    }

    /**
     * Creates a CryptByteBuffer instance using a byte array as the key.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The key bytes
     *
     * @throws GeneralSecurityException if any cryptographic error occurs
     */
    public CryptByteBuffer(Type type, byte[] key) throws GeneralSecurityException {
        this(type, KeyGenUtil.getSecretKey(type.keyType, key));
    }

    /**
     * Creates a CryptByteBuffer instance using a ByteBuffer as the key source.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The key bytes in ByteBuffer form
     *
     * @throws GeneralSecurityException if any cryptographic error occurs
     */
    public CryptByteBuffer(Type type, ByteBuffer key) throws GeneralSecurityException {
        this(type, Fields.copyToArray(key));
    }

    /**
     * Creates a CryptByteBuffer with specified key and IV from a byte array at given offset.
     *
     * @param type   The symmetric algorithm configuration to use
     * @param key    The secret key for cryptographic operations
     * @param iv     The byte array containing the IV
     * @param offset The starting position of the IV in the byte array
     *
     * @throws InvalidKeyException                if the key is invalid
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     */
    public CryptByteBuffer(Type type, SecretKey key, byte[] iv, int offset)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this(type, key, new IvParameterSpec(iv, offset, type.ivSize));
    }

    /**
     * Creates a CryptByteBuffer instance with specified algorithm type, key, and IV. The IV
     * will be used directly without offset considerations.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The secret key for cryptographic operations
     * @param iv   The initialization vector as a byte array
     *
     * @throws InvalidKeyException                if the key is invalid
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     */
    public CryptByteBuffer(Type type, SecretKey key, byte[] iv)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this(type, key, iv, 0);
    }

    /**
     * Creates a CryptByteBuffer instance using a ByteBuffer as the IV source. The IV will be
     * extracted from the ByteBuffer's current position.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The secret key for cryptographic operations
     * @param iv   The initialization vector as a ByteBuffer
     *
     * @throws InvalidKeyException                if the key is invalid
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     */
    public CryptByteBuffer(Type type, SecretKey key, ByteBuffer iv)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this(type, key, Fields.copyToArray(iv), 0);
    }

    /**
     * Creates a CryptByteBuffer instance using raw byte arrays for both key and IV. The IV
     * will be extracted from the specified offset position.
     *
     * @param type   The symmetric algorithm configuration to use
     * @param key    The key bytes
     * @param iv     The byte array containing the IV
     * @param offset The starting position of the IV in the byte array
     *
     * @throws InvalidKeyException                if the key is invalid
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     */
    public CryptByteBuffer(Type type, byte[] key, byte[] iv, int offset)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this(type, KeyGenUtil.getSecretKey(type.keyType, key), iv, offset);
    }

    /**
     * Creates a CryptByteBuffer instance using raw byte arrays for both key and IV. The IV
     * will be used directly without offset considerations.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The key bytes
     * @param iv   The initialization vector as a byte array
     *
     * @throws InvalidKeyException                if the key is invalid
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     */
    public CryptByteBuffer(Type type, byte[] key, byte[] iv)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this(type, key, iv, 0);
    }


    /**
     * Creates a CryptByteBuffer instance using ByteBuffers for both key and IV. Both key and
     * IV will be extracted from their respective ByteBuffer's current positions.
     *
     * @param type The symmetric algorithm configuration to use
     * @param key  The key bytes in ByteBuffer form
     * @param iv   The initialization vector in ByteBuffer form
     *
     * @throws InvalidKeyException                if the key is invalid
     * @throws InvalidAlgorithmParameterException if the IV parameters are invalid
     */
    public CryptByteBuffer(Type type, ByteBuffer key, ByteBuffer iv)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        this(type, Fields.copyToArray(key), Fields.copyToArray(iv), 0);
    }

    /**
     * Encrypts the provided byte array.
     *
     * @param input The data to be encrypted
     *
     * @return A new byte array containing the encrypted data
     *
     * @throws IllegalArgumentException if encryption fails
     */
    public byte[] encryptCopy(byte[] input) {
        try {
            return encryptCipher.doFinal(input);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalArgumentException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the provided byte array.
     *
     * @param input The data to be decrypted
     *
     * @return A new byte array containing the decrypted data
     *
     * @throws IllegalArgumentException if decryption fails
     */
    public byte[] decryptCopy(byte[] input) {
        try {
            return decryptCipher.doFinal(input);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalArgumentException("Decryption failed", e);
        }
    }

    /**
     * Generates a new random IV and reinitializes the ciphers. Not applicable for RijndaelPCFB
     * algorithm.
     */
    public void genIV() {
        this.iv = KeyGenUtil.genIV(type.ivSize);
        try {
            initializeCiphers();
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Should not happen", e);
        }
    }

    /**
     * Retrieves the current initialization vector.
     *
     * @return The current IV as IvParameterSpec, or null if not using an IV
     */
    public @Nullable IvParameterSpec getIV() {
        return iv;
    }

    /**
     * Updates the initialization vector and reinitializes the ciphers. Not applicable for
     * RijndaelPCFB algorithm.
     *
     * @param iv The new initialization vector
     *
     * @throws InvalidAlgorithmParameterException if the IV is invalid
     */
    public void setIV(@Nullable IvParameterSpec iv) throws InvalidAlgorithmParameterException {
        this.iv = iv;
        initializeCiphers();
    }

    /**
     * Initializes both encryption and decryption ciphers with the current key and IV.
     *
     * @throws IllegalStateException if cipher initialization fails
     */
    @SuppressWarnings("java:S6432")
    private void initializeCiphers() throws InvalidAlgorithmParameterException {
        try {
            encryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);
            decryptCipher.init(Cipher.DECRYPT_MODE, key, iv);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new InvalidAlgorithmParameterException("Failed to initialize ciphers", e);
        }
    }

    /**
     * The cryptographic algorithm type configuration being used.
     */
    private final Type type;
    /**
     * The secret key used for encryption and decryption operations.
     */
    private final SecretKey key;
    /**
     * The cipher instance used for encryption operations.
     */
    private final transient Cipher encryptCipher;
    /**
     * The cipher instance used for decryption operations.
     */
    private final transient Cipher decryptCipher;
    /**
     * The initialization vector specification.
     */
    private transient @Nullable IvParameterSpec iv;
}