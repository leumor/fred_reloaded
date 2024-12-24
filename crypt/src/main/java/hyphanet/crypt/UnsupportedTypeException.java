/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import java.io.Serial;


/**
 * Exception thrown to indicate that a method has been passed an unsupported enum value from
 * one of the various Type enums in the {@link hyphanet.crypt} package.
 * <p>
 * This exception is typically thrown when a cryptographic operation is attempted with an enum
 * type that is not supported by the implementing method.
 *
 * @author unixninja92
 */
public class UnsupportedTypeException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Constructs a new UnsupportedTypeException with a detailed error message including the
     * unsupported enum type and an additional explanation.
     *
     * @param type the enum value that was not supported
     * @param s    additional details about why the type is not supported
     */
    public UnsupportedTypeException(Enum<?> type, String s) {
        super("Unsupported " + type.getDeclaringClass().getName() + " " + type.name() +
              " used. " + s);
    }

    /**
     * Constructs a new UnsupportedTypeException with a basic error message including only the
     * unsupported enum type.
     *
     * @param type the enum value that was not supported
     */
    public UnsupportedTypeException(Enum<?> type) {
        this(type, "");
    }
}
