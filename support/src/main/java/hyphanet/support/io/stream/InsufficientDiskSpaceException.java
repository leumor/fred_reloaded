package hyphanet.support.io.stream;

import java.io.IOException;
import java.io.Serial;

/**
 * Signals that an operation failed because there is insufficient disk space. This exception is
 * typically thrown when attempting to write data to a disk or storage medium and there is not
 * enough space available to complete the operation.
 */
public class InsufficientDiskSpaceException extends IOException {
  @Serial private static final long serialVersionUID = 1795900904922247498L;
}
