/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.mac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Implements the HMAC (Hash-based Message Authentication Code) function as described in
 * <a href="https://csrc.nist.gov/publications/detail/fips/198/1/final">FIPS PUB 198-1</a>.
 * <p>
 * This implementation provides a secure way to create and verify message authentication codes
 * using the SHA-256 hash function.
 * </p>
 */
public enum Hmac {
    /**
     * SHA-256 HMAC implementation with a 32-byte (256-bit) digest size.
     */
    SHA2_256("HmacSHA256", 32);

    private static final Logger logger = LoggerFactory.getLogger(Hmac.class);

    /**
     * Constructs a new HMAC instance.
     *
     * @param algo The name of the HMAC algorithm
     * @param size The size of the digest in bytes
     */
    Hmac(String algo, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Digest size must be positive");
        }
        this.algo = algo;
        this.digestSize = size;
    }

    /**
     * Generates a Message Authentication Code (MAC) for the given data using the specified
     * key.
     *
     * @param hash The HMAC algorithm to use
     * @param key  The secret key for the HMAC
     * @param data The data to authenticate
     *
     * @return The generated MAC
     *
     * @throws IllegalArgumentException if the key length is incorrect
     */
    public static byte[] mac(Hmac hash, byte[] key, byte[] data) {
        if (key.length != hash.digestSize) {
            throw new IllegalArgumentException(String.format(
                "Invalid key size: expected %d bytes, got %d bytes",
                hash.digestSize,
                key.length
            ));
        }

        // Create defensive copies of the input arrays
        byte[] keyCopy = key.clone();
        byte[] dataCopy = data.clone();

        try {
            SecretKeySpec signingKey = new SecretKeySpec(keyCopy, hash.algo);
            Mac mac = Mac.getInstance(hash.algo);
            mac.init(signingKey);
            return mac.doFinal(dataCopy);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid key specification", e);
        }
    }

    /**
     * Verifies a Message Authentication Code (MAC) against the given data and key.
     *
     * @param hash The HMAC algorithm to use
     * @param key  The secret key for the HMAC
     * @param data The data to verify
     * @param mac  The MAC to verify against
     *
     * @return true if the MAC is valid, false otherwise
     */
    public static boolean verify(Hmac hash, byte[] key, byte[] data, byte[] mac) {
        return MessageDigest.isEqual(mac, mac(hash, key, data));
    }

    /**
     * Convenience method to generate a MAC using SHA-256.
     *
     * @param key  The secret key for the HMAC
     * @param text The data to authenticate
     *
     * @return The generated MAC
     */
    public static byte[] macWithSHA256(byte[] key, byte[] text) {
        return mac(Hmac.SHA2_256, key, text);
    }

    /**
     * Convenience method to verify a MAC using SHA-256.
     *
     * @param key  The secret key for the HMAC
     * @param text The data to verify
     * @param mac  The MAC to verify against
     *
     * @return true if the MAC is valid, false otherwise
     */
    public static boolean verifyWithSHA256(byte[] key, byte[] text, byte[] mac) {
        return verify(Hmac.SHA2_256, key, text, mac);
    }

    /**
     * The standard algorithm name for the HMAC implementation. This name must match the
     * algorithm names defined in the Java Security Standard Algorithm Names Specification.
     */
    final String algo;

    /**
     * The size of the message digest produced by this HMAC algorithm, in bytes. For SHA-256,
     * this is 32 bytes (256 bits).
     */
    final int digestSize;
}	
