/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.support.io.storage.bucket.Bucket;
import java.io.Serial;
import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for creating {@link ReaderBucket} instances. This factory manages a shared {@link
 * ReaderBucketState} for an underlying {@link Bucket}, allowing multiple {@link ReaderBucket}
 * instances to read from the same underlying data while ensuring the underlying bucket is disposed
 * of only when all readers are finished.
 *
 * <p>This class is serializable to allow for persistence and resumption of operations involving
 * read-only buckets.
 *
 * @author toad
 */
public class ReaderBucketFactory implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger(ReaderBucketFactory.class);

  /**
   * Constructs a {@link ReaderBucketFactory} for the given underlying {@link Bucket}.
   *
   * @param underlying The underlying {@link Bucket} to be wrapped in read-only buckets.
   */
  public ReaderBucketFactory(Bucket underlying) {
    state = new ReaderBucketState(underlying);
  }

  /**
   * Protected constructor for serialization purposes. It initializes the state to null, which
   * should be restored during deserialization.
   */
  protected ReaderBucketFactory() {
    // For serialization.
    state = null;
  }

  /**
   * Gets a new {@link ReaderBucket} instance associated with this factory's underlying bucket. Each
   * call increments the reference count in the shared {@link ReaderBucketState}.
   *
   * @return A new {@link ReaderBucket} instance, or {@code null} if the underlying bucket has been
   *     closed.
   * @throws IllegalStateException if the underlying bucket is already closed.
   */
  public @Nullable Bucket getReaderBucket() {
    assert state != null;
    try {
      var bucket = new ReaderBucket(state);
      logger.info(
          "getReaderBucket() returning {} for {} for {}",
          bucket,
          this,
          state.getUnderlyingBucketName());
      return bucket;
    } catch (IllegalStateException e) {
      logger.warn("The underlying bucket has been closed.", e);
      return null;
    }
  }

  /**
   * The shared state for all {@link ReaderBucket} instances created by this factory. This state
   * manages the underlying {@link Bucket} and the reference count of readers. It is final and
   * private to ensure immutability and controlled access.
   */
  private final ReaderBucketState state;
}
