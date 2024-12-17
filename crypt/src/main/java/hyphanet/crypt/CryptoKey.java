/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import hyphanet.support.HexUtil;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Represents a cryptographic key used for encryption and decryption operations. This class
 * encapsulates the necessary information for a cryptographic key, including its value,
 * algorithm, and format.
 *
 * <p>The key can be used in various cryptographic operations such as encryption, decryption,
 * signing, and verification.</p>
 *
 * <p>This is an abstract class, concrete implementations will define specific key types.</p>
 */
public interface CryptoKey extends Serializable {
    /**
     * Generates a fingerprint for the given BigInteger quantities.
     * <p>
     * This method uses SHA-1 to create a fingerprint from the provided BigInteger values. It's
     * typically used by subclasses to implement their specific fingerprint() method.
     * </p>
     *
     * @param quantities An array of BigInteger values to be included in the fingerprint.
     *
     * @return A byte array containing the SHA-1 hash of the provided quantities.
     */
    static byte[] fingerprint(BigInteger[] quantities) {
        try {
            var shactx = MessageDigest.getInstance(Util.HashAlgorithm.SHA1.getAlgorithmName(),
                                                   Util.MD_PROVIDERS.get(
                                                       Util.HashAlgorithm.SHA1));
            for (BigInteger quantity : quantities) {
                byte[] mpi = Util.calcMPIBytes(quantity);
                shactx.update(mpi, 0, mpi.length);
            }
            return shactx.digest();
        } catch (NoSuchAlgorithmException e) {
            // impossible
            throw new IllegalStateException("This should never happen");
        }

    }

    /**
     * Returns a string representation of the key type.
     *
     * @return A string representing the key type.
     */
    String keyType();

    /**
     * Generates a fingerprint for the key.
     *
     * @return A byte array containing the key's fingerprint.
     */
    byte[] fingerprint();

    /**
     * Converts the key to its byte representation.
     *
     * @return A byte array representing the key.
     */
    byte[] asBytes();

    /**
     * Provides a detailed string representation of the key.
     *
     * @return A string with detailed information about the key.
     */
    String toLongString();

    /**
     * Provides a string representation of the key.
     * <p>
     * This method returns a concise string representation of the key, including its type and a
     * shortened fingerprint.
     * </p>
     *
     * @return A string representation of the key in the format "keyType/shortFingerprint".
     */
    default String toDefaultString() {
        StringBuilder b = new StringBuilder(keyType().length() + 1 + 4);
        b.append(keyType()).append('/');
        HexUtil.bytesToHexAppend(fingerprint(), 16, 4, b);
        return b.toString();
    }
}
