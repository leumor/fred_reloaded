package hyphanet.support.io.storage;

import java.io.IOException;

public interface ToDiskMigratable {

  long creationTime();

  boolean migrateToDisk() throws IOException;
}
