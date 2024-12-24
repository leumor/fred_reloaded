/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.mac;

import hyphanet.crypt.key.KeyType;

import java.security.NoSuchAlgorithmException;

/**
 * An enumeration of Message Authentication Code (MAC) algorithms available in Hyphanet. Each
 * enum constant represents a specific MAC algorithm with its associated properties including
 * key type, algorithm name, and initialization vector (IV) length if required.
 *
 * <p>The supported MAC algorithms are:
 * <ul>
 *   <li>HMAC with SHA-256</li>
 *   <li>HMAC with SHA-384</li>
 *   <li>HMAC with SHA-512</li>
 *   <li>Poly1305-AES</li>
 * </ul>
 * </p>
 *
 * @author unixninja92
 * @see javax.crypto.Mac
 * @see hyphanet.crypt.key.KeyType
 */
public enum MacType {
    /**
     * HMAC implementation using SHA-256 hash function
     */
    HMAC_SHA_256(1, "HMACSHA256", KeyType.HMAC_SHA_256),

    /**
     * HMAC implementation using SHA-384 hash function
     */
    HMAC_SHA_384(2, "HMACSHA384", KeyType.HMAC_SHA_384),

    /**
     * HMAC implementation using SHA-512 hash function
     */
    HMAC_SHA_512(2, "HMACSHA512", KeyType.HMAC_SHA_512),

    /**
     * Poly1305-AES authentication algorithm with 16-byte IV
     */
    POLY1305_AES(2, "POLY1305-AES", 16, KeyType.POLY1305_AES);

    /**
     * Bitmask used for algorithm aggregation and compatibility checks.
     */
    public final int bitmask;

    /**
     * The name of the MAC algorithm.
     */
    public final String algName;

    /**
     * The length of the initialization vector (IV) in bytes. A value of -1 indicates that no
     * IV is required.
     */
    public final int ivLen;

    /**
     * The type of cryptographic key required by this MAC algorithm.
     */
    public final KeyType keyType;

    /**
     * Constructs a MAC algorithm type that doesn't require an initialization vector.
     *
     * @param bitmask The bitmask value for algorithm aggregation
     * @param algName The name of the MAC algorithm
     * @param type    The required key type for this algorithm
     */
    MacType(int bitmask, String algName, KeyType type) {
        this.bitmask = bitmask;
        this.algName = algName;
        ivLen = -1;
        keyType = type;
    }

    /**
     * Constructs a MAC algorithm type that requires an initialization vector.
     *
     * @param bitmask The bitmask value for algorithm aggregation
     * @param algName The name of the MAC algorithm
     * @param ivLen   The length of the initialization vector in bytes
     * @param type    The required key type for this algorithm
     */
    MacType(int bitmask, String algName, int ivLen, KeyType type) {
        this.bitmask = bitmask;
        this.algName = algName;
        this.ivLen = ivLen;
        keyType = type;
    }

    /**
     * Creates a new instance of the MAC algorithm.
     *
     * @return A new {@link javax.crypto.Mac} instance for this algorithm
     */
    public final javax.crypto.Mac get() {
        try {
            return javax.crypto.Mac.getInstance(algName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This should never happen", e);
        }
    }

}
