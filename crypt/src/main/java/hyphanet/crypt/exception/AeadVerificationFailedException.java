package hyphanet.crypt.exception;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown when the final MAC fails on an AEADInputStream.
 */
public class AeadVerificationFailedException extends IOException {
    @Serial
    private static final long serialVersionUID = 4850585521631586023L;
}
