/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.field;

import java.io.Serial;

/**
 * Exception thrown when parsing errors occur while processing a SimpleFieldSet format. This
 * exception indicates that the input data could not be properly parsed according to the
 * expected format specifications.
 *
 * <p>Common scenarios where this exception might be thrown include:</p>
 * <ul>
 *   <li>Invalid key-value pair format</li>
 *   <li>Malformed nested structure</li>
 *   <li>Number format errors when parsing numeric values</li>
 *   <li>Missing required fields</li>
 *   <li>Invalid Base64 encoding</li>
 * </ul>
 *
 * @see SimpleFieldSet
 * @since 1.0
 */
public class FSParseException extends Exception {
    /**
     * Serial version UID for serialization compatibility.
     */
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Constructs a new FSParseException wrapping another exception.
     *
     * @param e the underlying exception that caused the parse failure
     */
    public FSParseException(Exception e) {
        super(e);
    }

    /**
     * Constructs a new FSParseException with a detailed error message.
     *
     * @param msg the detail message describing the parse error
     */
    public FSParseException(String msg) {
        super(msg);
    }

    /**
     * Constructs a new FSParseException with a detailed error message and the underlying
     * NumberFormatException that caused the parse failure.
     *
     * <p>This constructor is specifically used when numeric parsing fails
     * during field set processing.</p>
     *
     * @param msg the detail message describing the parse error
     * @param e   the NumberFormatException that caused the parse failure
     */
    public FSParseException(String msg, NumberFormatException e) {
        super(msg + " : " + e);
        initCause(e);
    }

}
