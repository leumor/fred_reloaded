package hyphanet.support.io.storage;

import java.io.IOException;

public interface TempStorage extends Storage {

  long creationTime();

  boolean migrateToDisk() throws IOException;

  Storage getUnderlying();

  default boolean isRamStorage() {
    return getUnderlying() instanceof RamStorage;
  }
}
