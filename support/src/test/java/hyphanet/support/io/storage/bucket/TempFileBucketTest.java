/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hyphanet.support.io.FilenameGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class TempFileBucketTest extends BucketTestBase {
  @Override
  protected Bucket makeBucket(long size) throws IOException {
    FilenameGenerator filenameGenerator = new FilenameGenerator(weakPRNG, false, null, "junit");
    BaseFileBucket bfb =
        new TempFileBucket(filenameGenerator.makeRandomFilename(), filenameGenerator);

    assertTrue(bfb.deleteOnDispose(), "deleteOnDispose");

    return bfb;
  }

  @Override
  protected void freeBucket(Bucket bucket) throws IOException {
    Path path = ((BaseFileBucket) bucket).getPath();
    if (bucket.size() != 0) assertTrue(Files.exists(path), "TempFile not exist");

    bucket.dispose();
    assertFalse(Files.exists(path), "TempFile not deleted");
  }

  private final Random weakPRNG = new Random(12345);
}
