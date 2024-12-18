package hyphanet.crypt.blockcipher;

import java.io.Serial;

/**
 * Exception thrown when an unsupported cipher configuration is encountered. This includes
 * scenarios such as invalid key sizes, unsupported block sizes, or unavailable cipher
 * algorithms.
 *
 * <p>This exception is typically thrown by cipher implementations when:</p>
 * <ul>
 *   <li>An invalid key size is specified</li>
 *   <li>An unsupported block size is requested</li>
 *   <li>The requested cipher algorithm is not available</li>
 * </ul>
 *
 * @since 1.0
 */
public class UnsupportedCipherException extends Exception {
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Constructs a new {@code UnsupportedCipherException} with no detail message.
     */
    public UnsupportedCipherException() {
    }

    /**
     * Constructs a new {@code UnsupportedCipherException} with the specified detail message.
     *
     * @param message the detail message describing the cause of this exception. The detail
     *                message is saved for later retrieval by the {@link #getMessage()}
     *                method.
     */
    public UnsupportedCipherException(String message) {
        super(message);
    }
}
