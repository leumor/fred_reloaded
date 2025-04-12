package hyphanet.support.io.storage;

import java.io.IOException;

public interface RamStorage {
  /**
   * Creates a copy of the internal byte array.
   *
   * @return A new byte array containing a copy of the storage's data
   * @throws IOException if the storage has been closed
   */
  byte[] toByteArray() throws IOException;
}
