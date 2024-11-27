package hyphanet.support;

import java.io.Serial;

/**
 * Custom exception thrown during Base64 encoding/decoding operations.
 *
 * <p>This exception indicates invalid Base64 data, which can occur in the following cases:</p>
 * <ul>
 *   <li>Input string has an invalid length</li>
 *   <li>Input contains characters not in the Base64 alphabet</li>
 *   <li>Malformed padding characters</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * try {
 *     byte[] decoded = Base64.decode(encodedString);
 * } catch (IllegalBase64Exception e) {
 *     // Handle invalid Base64 data
 * }
 * </pre>
 *
 * @see Base64
 */
public class IllegalBase64Exception extends Exception {

    /**
     * Serial version UID for serialization compatibility. A negative value is used to indicate no
     * compatibility guarantees.
     */
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Constructs a new IllegalBase64Exception with the specified error message.
     *
     * @param descr detailed description of the error condition
     */
    public IllegalBase64Exception(String descr) {
        super(descr);
    }
}
