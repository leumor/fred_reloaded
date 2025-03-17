/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketTestBase;
import hyphanet.support.io.storage.bucket.TempFileBucket;
import java.io.IOException;
import java.util.Random;
import java.util.random.RandomGenerator;

public class PaddedEphemerallyEncryptedBucketTest extends BucketTestBase {
  @Override
  protected Bucket makeBucket(long size) throws IOException {
    FilenameGenerator filenameGenerator = new FilenameGenerator(weakPRNG, false, null, "junit");
    TempFileBucket fileBucket =
        new TempFileBucket(filenameGenerator.makeRandomFilename(), filenameGenerator);
    return new PaddedEphemerallyEncryptedBucket(fileBucket, 1024, strongPRNG, weakPRNG);
  }

  @Override
  protected void freeBucket(Bucket bucket) throws IOException {
    bucket.dispose();
  }

  private final RandomGenerator strongPRNG = RandomGenerator.getDefault();
  private final Random weakPRNG = new Random(54321);
}
