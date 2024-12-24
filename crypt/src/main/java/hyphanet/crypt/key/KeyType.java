/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.key;

/**
 * An enumeration of cryptographic key types supported by Hyphanet, defining their properties
 * and characteristics.
 * <p>
 * This enum maintains the specifications for various cryptographic algorithms, including:
 * <ul>
 *   <li>Algorithm names for key generation</li>
 *   <li>Key sizes in bits</li>
 *   <li>Initialization vector (IV) sizes in bits</li>
 * </ul>
 * </p>
 *
 * @author unixninja92
 */
public enum KeyType {
    /**
     * Rijndael cipher with 128-bit key length
     */
    RIJNDAEL_128("RIJNDAEL", 128),

    /**
     * Rijndael cipher with 256-bit key length
     */
    RIJNDAEL_256("RIJNDAEL", 256),

    /**
     * AES (Advanced Encryption Standard) with 256-bit key length
     */
    AES_128("AES", 128),

    /**
     * AES (Advanced Encryption Standard) with 256-bit key length
     */
    AES_256("AES", 256),

    /**
     * HMAC using SHA-256 hash function with 256-bit key length
     */
    HMAC_SHA_256("HMACSHA256", 256),

    /**
     * HMAC using SHA-384 hash function with 384-bit key length
     */
    HMAC_SHA_384("HMACSHA384", 384),

    /**
     * HMAC using SHA-512 hash function with 512-bit key length
     */
    HMAC_SHA_512("HMACSHA512", 512),

    /**
     * Poly1305-AES authentication with 256-bit key and 128-bit IV
     */
    POLY1305_AES("POLY1305-AES", 256, 128),

    /**
     * ChaCha stream cipher with 128-bit key and 64-bit IV
     */
    CHACHA_128("CHACHA", 128, 64),

    /**
     * ChaCha stream cipher with 256-bit key and 64-bit IV
     */
    CHACHA_256("CHACHA", 256, 64);

    /**
     * The algorithm name used by KeyGenerator
     */
    public final String algName;

    /**
     * The key size in bits
     */
    public final int keySize; //bits

    /**
     * The initialization vector (IV) size in bits
     */
    public final int ivSize; //bits

    /**
     * Constructs a KeyType with the specified algorithm name and key size. The IV size is set
     * equal to the key size.
     *
     * @param algName The name of the algorithm to be used by KeyGenerator
     * @param keySize The size of the key in bits
     */
    KeyType(String algName, int keySize) {
        this.algName = algName;
        this.keySize = keySize;
        this.ivSize = keySize;
    }

    /**
     * Constructs a KeyType with the specified algorithm name, key size, and IV size.
     *
     * @param algName The name of the algorithm to be used by KeyGenerator
     * @param keySize The size of the key in bits
     * @param ivSize  The size of the initialization vector (IV) in bits
     */
    KeyType(String algName, int keySize, int ivSize) {
        this.algName = algName;
        this.keySize = keySize;
        this.ivSize = ivSize;
    }
}
