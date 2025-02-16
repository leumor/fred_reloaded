package hyphanet.support.io.storage.bucket;

import java.io.IOException;

/**
 * A {@link BucketFactory} that creates {@link NullBucket} instances.
 *
 * <p>This factory is useful for testing and situations where a bucket is needed but no actual
 * storage is required.
 */
public class NullBucketFactory implements BucketFactory {

  /**
   * Creates a new {@link NullBucket} instance.
   *
   * @param size The pre-defined size of the {@link NullBucket}.
   * @return A new {@link NullBucket} instance.
   * @throws IOException Never, as {@link NullBucket} creation does not throw {@link IOException}.
   */
  @Override
  public RandomAccessible makeBucket(long size) throws IOException {
    return new NullBucket(size);
  }
}
