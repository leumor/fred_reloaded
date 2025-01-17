/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import hyphanet.crypt.key.KeyType;

import java.io.Serializable;

/**
 * Represents the cryptographic algorithm configurations available for byte buffer encryption
 * in Hyphanet. This enum defines various symmetric cipher algorithms with their respective
 * properties including block sizes, initialization vector (IV) requirements, and key
 * specifications.
 * <p>
 * Each enum constant provides a complete configuration for a specific encryption algorithm,
 * enabling consistent and secure cryptographic operations throughout the system.
 * </p>
 *
 * @author unixninja92
 * @see hyphanet.crypt.CryptByteBuffer
 */
public enum CryptByteBufferType implements Serializable {
    /**
     * Legacy RIJNDAEL implementation using PCFB mode.
     *
     * @deprecated Use {@link #AES_CTR} or {@link #CHACHA_128} instead for better security
     */
    @Deprecated RIJNDAEL_PCFB(8, 32, "RIJNDAEL256/CFB/NoPadding", KeyType.RIJNDAEL_256),

    /**
     * AES cipher in Counter (CTR) mode with 256-bit key strength. Provides strong encryption
     * with parallel processing capabilities.
     */
    AES_CTR(16, 16, "AES/CTR/NOPADDING", KeyType.AES_256),

    /**
     * ChaCha stream cipher with 128-bit security strength. Offers high-speed encryption on
     * software implementations.
     */
    CHACHA_128(32, 8, "CHACHA", KeyType.CHACHA_128),

    /**
     * ChaCha stream cipher with 256-bit security strength. Provides extended security margin
     * compared to CHACHA_128.
     */
    CHACHA_256(64, 8, "CHACHA", KeyType.CHACHA_256);

    /**
     * Bitmask used for algorithm aggregation and identification. This value must be positive
     * and unique for each algorithm type.
     */
    public final int bitmask;

    /**
     * The cipher's block size in bytes. For stream ciphers, this represents the internal state
     * size.
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
     * The key type specification for this cipher. Defines the key generation parameters and
     * constraints.
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
     * @throws IllegalArgumentException if bitmask is not positive, ivSize is not positive, or
     *                                  algName is blank
     */
    CryptByteBufferType(int bitmask, int ivSize, String algName, KeyType keyType) {

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
