package hyphanet.crypt.exception;

import java.io.IOException;
import java.io.Serial;

/**
 * Exception thrown when there is an error in cryptographic data formatting. This exception is
 * typically thrown when cryptographic data cannot be properly parsed, encoded, or decoded.
 */
public class CryptFormatException extends Exception {

    @Serial
    private static final long serialVersionUID = -796276279268900609L;

    /**
     * Constructs a new CryptFormatException with the specified detail message.
     *
     * @param message the detail message describing the cause of the exception
     */
    public CryptFormatException(String message) {
        super(message);
    }

    /**
     * Constructs a new CryptFormatException that wraps an IOException. The message from the
     * IOException is used as this exception's detail message.
     *
     * @param e the IOException to be wrapped
     */
    public CryptFormatException(IOException e) {
        super(e.getMessage());
        initCause(e);
    }

    /**
     * Constructs a new CryptFormatException with the specified detail message and cause.
     *
     * @param message the detail message describing the cause of the exception
     * @param e       the underlying cause of this exception
     */
    public CryptFormatException(String message, Exception e) {
        super(message);
        initCause(e);
    }

}
