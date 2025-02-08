package hyphanet.support.io.storage;

import java.io.IOException;
import java.lang.ref.WeakReference;

public interface TempStorage extends Storage {

  long creationTime();

  boolean migrateToDisk() throws IOException;

  WeakReference<TempStorage> getReference();
}
