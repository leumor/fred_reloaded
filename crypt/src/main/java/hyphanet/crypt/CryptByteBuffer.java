/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import hyphanet.base.Fields;
import hyphanet.crypt.key.KeyGenUtil;
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
 * Recommended algorithm: {@link CryptByteBufferType#CHACHA_128}
 * </p>
 *
 * @author unixninja92
 * @see CryptByteBufferType
 */
public final class CryptByteBuffer implements Serializable {

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
    public CryptByteBuffer(
        CryptByteBufferType type,
        SecretKey key,
        @Nullable IvParameterSpec iv
    ) throws InvalidKeyException, InvalidAlgorithmParameterException {
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
    public CryptByteBuffer(CryptByteBufferType type, SecretKey key)
        throws GeneralSecurityException {
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
    public CryptByteBuffer(CryptByteBufferType type, byte[] key)
        throws GeneralSecurityException {
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
    public CryptByteBuffer(CryptByteBufferType type, ByteBuffer key)
        throws GeneralSecurityException {
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
    public CryptByteBuffer(CryptByteBufferType type, SecretKey key, byte[] iv, int offset)
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
    public CryptByteBuffer(CryptByteBufferType type, SecretKey key, byte[] iv)
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
    public CryptByteBuffer(CryptByteBufferType type, SecretKey key, ByteBuffer iv)
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
    public CryptByteBuffer(CryptByteBufferType type, byte[] key, byte[] iv, int offset)
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
    public CryptByteBuffer(CryptByteBufferType type, byte[] key, byte[] iv)
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
    public CryptByteBuffer(CryptByteBufferType type, ByteBuffer key, ByteBuffer iv)
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
    private final CryptByteBufferType type;

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