/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.key;

import hyphanet.base.Fields;
import hyphanet.crypt.Global;
import hyphanet.crypt.UnsupportedTypeException;
import hyphanet.crypt.mac.Mac;
import hyphanet.crypt.mac.MacType;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


/**
 * A utility class providing cryptographic key generation and manipulation functionality. This
 * class offers methods to generate and convert various types of cryptographic keys,
 * initialization vectors (IVs), and nonces.
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Generation of public/private key pairs</li>
 *   <li>Conversion between different key formats</li>
 *   <li>Secret key generation and manipulation</li>
 *   <li>IV and nonce generation</li>
 *   <li>Key derivation functions</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This class cannot handle DSA keys and will throw exceptions for
 * DSA-related operations.</p>
 *
 * @author unixninja92
 * @see javax.crypto.KeyGenerator
 * @see java.security.KeyPair
 * @see javax.crypto.SecretKey
 */
public final class KeyGenUtil {

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always, as this class should not be instantiated
     */
    private KeyGenUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Generates a new public/private key pair for the specified algorithm type.
     *
     * @param type the {@link KeyPairType} specifying the algorithm format for key pair
     *             generation
     *
     * @return a new {@link KeyPair} containing the generated public and private keys
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws SecurityException        if key pair generation fails due to algorithm
     *                                  unavailability or invalid parameters
     */
    public static KeyPair genKeyPair(KeyPairType type) {
        if (type.equals(KeyPairType.DSA)) {
            throw new UnsupportedTypeException(type);
        }
        try {
            var kg = KeyPairGenerator.getInstance(type.alg);
            kg.initialize(type.spec);
            return kg.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new SecurityException("Failed to generate key pair", e);
        }
    }

    /**
     * Converts a byte array representation of a public key into a {@link PublicKey} object.
     *
     * @param type the {@link KeyPairType} specifying the algorithm of the key
     * @param pub  the byte array containing the encoded public key
     *
     * @return a {@link PublicKey} object representing the input key data
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws IllegalArgumentException if the provided key data is invalid
     */
    public static PublicKey getPublicKey(KeyPairType type, byte[] pub) {
        if (type.equals(KeyPairType.DSA)) {
            throw new UnsupportedTypeException(type);
        }
        try {
            var kf = KeyFactory.getInstance(type.alg);
            X509EncodedKeySpec xks = new X509EncodedKeySpec(pub);
            return kf.generatePublic(xks);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid public key data", e);
        }
    }

    /**
     * Creates a {@link PublicKey} from a public key stored in a {@link ByteBuffer}.
     *
     * @param type the {@link KeyPairType} specifying the algorithm of the key
     * @param pub  the public key data as a {@link ByteBuffer}
     *
     * @return a {@link PublicKey} object representing the input key data
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws IllegalArgumentException if the provided key data is invalid
     * @see #getPublicKey(KeyPairType, byte[])
     */
    public static PublicKey getPublicKey(KeyPairType type, ByteBuffer pub) {
        return getPublicKey(type, Fields.copyToArray(pub));
    }

    /**
     * Converts a specified key for a specified algorithm to a {@link PublicKey} which is then
     * stored in a {@link KeyPair}. The private key of the KeyPair is null.
     *
     * @param type the {@link KeyPairType} specifying the algorithm format
     * @param pub  the public key as a {@link ByteBuffer}
     *
     * @return a {@link KeyPair} containing the public key and a null private key
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws IllegalArgumentException if the provided key data is invalid
     * @see #getPublicKey(KeyPairType, byte[])
     */
    public static KeyPair getPublicKeyPair(KeyPairType type, byte[] pub) {
        return getKeyPair(getPublicKey(type, pub), null);
    }

    /**
     * Converts a specified key for a specified algorithm to a {@link PublicKey} which is then
     * stored in a {@link KeyPair}. The private key of the KeyPair is null.
     *
     * <p>This method first converts the {@link ByteBuffer} input to a byte array, then
     * processes it to create a public key. The resulting {@link KeyPair} will have a null
     * private key component.</p>
     *
     * @param type the {@link KeyPairType} specifying the algorithm format
     * @param pub  the public key as a {@link ByteBuffer}
     *
     * @return a {@link KeyPair} containing the public key and a null private key
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws IllegalArgumentException if the provided key data is invalid
     * @see #getPublicKeyPair(KeyPairType, byte[])
     */
    public static KeyPair getPublicKeyPair(KeyPairType type, ByteBuffer pub) {
        return getPublicKeyPair(type, Fields.copyToArray(pub));
    }

    /**
     * Converts the specified keys for a specified algorithm to {@link PrivateKey} and
     * {@link PublicKey} respectively, storing them in a {@link KeyPair}.
     *
     * @param type the {@link KeyPairType} specifying the algorithm format
     * @param pub  the public key as a byte array
     * @param pri  the private key as a byte array
     *
     * @return a {@link KeyPair} containing both the public and private keys
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws IllegalArgumentException if either key data is invalid
     */
    public static KeyPair getKeyPair(KeyPairType type, byte[] pub, byte[] pri) {
        if (type == KeyPairType.DSA) {
            throw new UnsupportedTypeException(type);
        }
        try {
            var kf = KeyFactory.getInstance(type.alg);
            PublicKey pubK = getPublicKey(type, pub);
            PrivateKey privK = kf.generatePrivate(new PKCS8EncodedKeySpec(pri));
            // FIXME verify that the keys are consistent if assertions/logging enabled??
            return getKeyPair(pubK, privK);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid key data", e);
        }
    }

    /**
     * Converts the specified keys for a specified algorithm to {@link PrivateKey} and
     * {@link PublicKey} respectively, storing them in a {@link KeyPair}.
     *
     * <p>This method first converts the {@link ByteBuffer} inputs to byte arrays, then
     * processes them to create the appropriate key objects.</p>
     *
     * @param type the {@link KeyPairType} specifying the algorithm format
     * @param pub  the public key as a {@link ByteBuffer}
     * @param pri  the private key as a {@link ByteBuffer}
     *
     * @return a {@link KeyPair} containing both the public and private keys
     *
     * @throws UnsupportedTypeException if the type is DSA, which is not supported
     * @throws IllegalArgumentException if either key data is invalid
     * @see #getKeyPair(KeyPairType, byte[], byte[])
     */
    public static KeyPair getKeyPair(KeyPairType type, ByteBuffer pub, ByteBuffer pri) {
        return getKeyPair(type, Fields.copyToArray(pub), Fields.copyToArray(pri));
    }

    /**
     * Creates a {@link KeyPair} from the provided public and private keys.
     *
     * @param pubK  the public key component
     * @param privK the private key component, may be null
     *
     * @return a {@link KeyPair} containing the provided keys
     */
    public static KeyPair getKeyPair(PublicKey pubK, PrivateKey privK) {
        return new KeyPair(pubK, privK);
    }

    /**
     * Generates a random secret key for the specified symmetric algorithm.
     *
     * @param type the {@link KeyType} specifying the algorithm and key size
     *
     * @return a new {@link SecretKey} generated according to the specified parameters
     *
     * @throws SecurityException if key generation fails due to algorithm unavailability
     */

    public static SecretKey genSecretKey(KeyType type) {
        try {
            var kg = KeyGenerator.getInstance(type.algName);
            kg.init(type.keySize);
            return kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("Failed to generate secret key", e);
        }
    }

    /**
     * Creates a {@link SecretKey} from the specified key bytes for the given algorithm type.
     *
     * <p><strong>Note:</strong> For HMAC keys, any key length is acceptable. For other
     * algorithms, the key length must match the specified key size of the algorithm.</p>
     *
     * @param key  the key data as a byte array
     * @param type the {@link KeyType} specifying the algorithm and key parameters
     *
     * @return a new {@link SecretKey} instance
     *
     * @throws IllegalArgumentException if the key length doesn't match the required size for
     *                                  non-HMAC algorithms
     */
    public static SecretKey getSecretKey(KeyType type, byte[] key) {
        if (!type.name().startsWith("HMAC") && key.length != type.keySize >> 3) {
            throw new IllegalArgumentException(String.format(
                "Key size %d does not match " + "required size %d",
                key.length,
                type.keySize >> 3
            ));
        }
        return new SecretKeySpec(key.clone(), type.algName);
    }

    /**
     * Creates a {@link SecretKey} from key data stored in a {@link ByteBuffer}.
     *
     * @param key  the key data as a {@link ByteBuffer}
     * @param type the {@link KeyType} specifying the algorithm and key parameters
     *
     * @return a new {@link SecretKey} instance
     *
     * @throws IllegalArgumentException if the key length doesn't match the required size for
     *                                  non-HMAC algorithms
     * @see #getSecretKey(KeyType, byte[])
     */
    public static SecretKey getSecretKey(KeyType type, ByteBuffer key) {
        return getSecretKey(type, Fields.copyToArray(key));
    }

    /**
     * Generates a cryptographically secure random nonce of the specified length.
     *
     * @param length the desired length of the nonce in bytes
     *
     * @return a read-only {@link ByteBuffer} containing the generated nonce
     */
    public static ByteBuffer genNonce(int length) {
        return ByteBuffer.wrap(genRandomBytes(length)).asReadOnlyBuffer();
    }

    /**
     * Generates a cryptographically secure initialization vector (IV) of the specified
     * length.
     *
     * @param length the desired length of the IV in bytes
     *
     * @return an {@link IvParameterSpec} containing the generated IV
     */
    @SuppressWarnings("java:S3329")
    public static IvParameterSpec genIV(int length) {
        return new IvParameterSpec(genRandomBytes(length));
    }

    /**
     * Creates an {@link IvParameterSpec} from a portion of a byte array.
     *
     * @param iv     the byte array containing the IV data
     * @param offset the starting position of the IV in the array
     * @param length the length of the IV in bytes
     *
     * @return an {@link IvParameterSpec} containing the specified IV data
     *
     * @throws IllegalArgumentException if the offset or length are invalid
     */
    public static IvParameterSpec getIvParameterSpec(byte[] iv, int offset, int length) {
        return new IvParameterSpec(iv, offset, length);
    }

    /**
     * Creates an {@link IvParameterSpec} from initialization vector data stored in a
     * {@link ByteBuffer}.
     *
     * @param iv the {@link ByteBuffer} containing the IV data
     *
     * @return an {@link IvParameterSpec} containing the specified IV data
     */
    @SuppressWarnings("java:S3329")
    public static IvParameterSpec getIvParameterSpec(ByteBuffer iv) {
        return new IvParameterSpec(Fields.copyToArray(iv));
    }

    /**
     * Derives a secret key using a key derivation function (KDF) based on HMAC-SHA-512.
     *
     * @param kdfKey    the base key used for derivation
     * @param c         the class whose name will be used in the derivation process
     * @param kdfString additional string input for the derivation process
     * @param type      the desired type of the derived key
     *
     * @return a new {@link SecretKey} derived from the input parameters
     *
     * @throws InvalidKeyException if the base key is invalid for the derivation process
     */
    public static SecretKey deriveSecretKey(
        SecretKey kdfKey, Class<?> c, String kdfString,
        KeyType type) throws InvalidKeyException {
        return getSecretKey(
            type,
            deriveBytesTruncated(kdfKey, c, kdfString, type.keySize >> 3)
        );
    }

    /**
     * Derives an initialization vector (IV) using a key derivation function based on
     * HMAC-SHA-512.
     *
     * @param kdfKey    the base key used for derivation
     * @param c         the class whose name will be used in the derivation process
     * @param kdfString additional string input for the derivation process
     * @param ivType    the {@link KeyType} specifying the IV parameters
     *
     * @return an {@link IvParameterSpec} containing the derived IV
     *
     * @throws InvalidKeyException if the base key is invalid for the derivation process
     */
    public static IvParameterSpec deriveIvParameterSpec(
        SecretKey kdfKey, Class<?> c,
        String kdfString, KeyType ivType)
        throws InvalidKeyException {
        return getIvParameterSpec(deriveBytesTruncated(
            kdfKey,
            c,
            kdfString,
            ivType.ivSize >> 3
        ));
    }

    /**
     * Generates a specified number of cryptographically secure random bytes.
     *
     * @param length the number of random bytes to generate
     *
     * @return a byte array containing the random data
     */
    private static byte[] genRandomBytes(int length) {
        byte[] randBytes = new byte[length];
        Global.SECURE_RANDOM.nextBytes(randBytes);
        return randBytes;
    }

    /**
     * Derives a 512-bit (64-byte) value using HMAC-SHA-512 as the key derivation function.
     *
     * @param kdfKey    the base key used for derivation
     * @param c         the class whose name will be used in the derivation process
     * @param kdfString additional string input for the derivation process
     *
     * @return a read-only {@link ByteBuffer} containing the derived 512-bit value
     *
     * @throws InvalidKeyException if the base key is invalid for the derivation process
     */
    private static ByteBuffer deriveBytes(SecretKey kdfKey, Class<?> c, String kdfString)
        throws InvalidKeyException {
        Mac kdf = new Mac(MacType.HMAC_SHA_512, kdfKey);
        return kdf.genMac((c.getName() + kdfString).getBytes(StandardCharsets.UTF_8))
                  .asReadOnlyBuffer();
    }

    /**
     * Derives a value of specified length using HMAC-SHA-512 as the key derivation function.
     *
     * @param kdfKey    the base key used for derivation
     * @param c         the class whose name will be used in the derivation process
     * @param kdfString additional string input for the derivation process
     * @param len       the desired length of the derived value in bytes
     *
     * @return a read-only {@link ByteBuffer} containing the derived value
     *
     * @throws InvalidKeyException if the base key is invalid for the derivation process
     */
    private static ByteBuffer deriveBytesTruncated(
        SecretKey kdfKey, Class<?> c,
        String kdfString, int len)
        throws InvalidKeyException {
        byte[] key = new byte[len];
        deriveBytes(kdfKey, c, kdfString).get(key);
        return ByteBuffer.wrap(key).asReadOnlyBuffer();
    }
}
