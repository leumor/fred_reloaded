/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.key;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.Serial;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.util.Objects;

/**
 * A serializable secret key container used to derive various cryptographic keys and
 * initialization vectors for local storage in Hyphanet. This class serves as the root of a key
 * hierarchy, where multiple derived keys can be generated deterministically from a single
 * master secret.
 *
 * <p>The master secret is internally stored as an HMAC-SHA-512 key and can be
 * created either from random data or from an existing 64-byte secret.</p>
 *
 * <p>This class is immutable and thread-safe.</p>
 *
 * <p><b>Security Note:</b> The master secret should be stored securely and never
 * exposed outside the application.</p>
 *
 * @author unixninja92
 * @see KeyGenUtil
 * @see KeyType
 */
public final class MasterSecret implements Serializable {
    @Serial
    private static final long serialVersionUID = -8411217325990445764L;

    /**
     * Creates a new {@code MasterSecret} with a randomly generated master key. The key is
     * generated using {@link KeyGenUtil#genSecretKey} with {@link KeyType#HMAC_SHA_512}.
     */
    public MasterSecret() {
        masterKey = KeyGenUtil.genSecretKey(KeyType.HMAC_SHA_512);
    }

    /**
     * Creates a new {@code MasterSecret} from an existing secret byte array.
     *
     * @param secret the byte array containing the secret key material
     *
     * @throws IllegalArgumentException if the secret length is not exactly 64 bytes
     */
    public MasterSecret(byte[] secret) {
        if (secret.length != 64) {
            throw new IllegalArgumentException(
                "Secret must be exactly 64 bytes long, got " + secret.length);
        }
        masterKey = KeyGenUtil.getSecretKey(KeyType.HMAC_SHA_512, secret.clone());
    }

    /**
     * Derives a new {@link SecretKey} of the specified type from this master secret. The
     * derivation process is deterministic, meaning the same input will always produce the same
     * output key.
     *
     * @param type the type of key to derive, must not be null
     *
     * @return a new SecretKey of the specified type
     *
     * @throws IllegalStateException if key derivation fails
     * @see KeyType
     */
    public SecretKey deriveKey(KeyType type) {
        try {
            return KeyGenUtil.deriveSecretKey(
                masterKey,
                getClass(),
                type.name() + " key",
                type
            );
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Failed to derive key of type " + type, e);
        }
    }

    /**
     * Derives a new {@link IvParameterSpec} of the specified type from this master secret. The
     * derivation process is deterministic, meaning the same input will always produce the same
     * output IV.
     *
     * @param type the type of IV to derive, must not be null
     *
     * @return a new IvParameterSpec of the specified type
     *
     * @throws IllegalStateException if IV derivation fails
     * @see KeyType
     */
    public IvParameterSpec deriveIv(KeyType type) {
        try {
            return KeyGenUtil.deriveIvParameterSpec(
                masterKey,
                getClass(),
                type.name() + " iv",
                type
            );
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Failed to derive IV of type " + type, e);
        }
    }

    /**
     * Compares this {@code MasterSecret} with another object for equality. Two
     * {@code MasterSecret} instances are equal if they have equal master keys.
     *
     * @param obj the object to compare with
     *
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof MasterSecret other && Objects.equals(masterKey, other.masterKey);
    }

    /**
     * Returns a hash code value for this {@code MasterSecret}.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(masterKey);
    }


    /**
     * The master key used for deriving other keys and IVs. This key is of type HMAC-SHA-512
     * and is immutable once set.
     */
    private final SecretKey masterKey;
}
