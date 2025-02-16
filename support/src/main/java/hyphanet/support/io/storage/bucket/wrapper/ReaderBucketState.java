package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.support.io.storage.bucket.Bucket;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the shared state for {@link ReaderBucket} instances. This state includes the underlying
 * {@link Bucket}, a reference count of active readers, and a lock for thread-safe access to the
 * state. It ensures that the underlying bucket is disposed of only when all associated {@link
 * ReaderBucket} instances are closed or garbage collected.
 *
 * <p>This class is serializable to allow for persistence and resumption of operations involving
 * read-only buckets.
 */
public class ReaderBucketState implements Serializable {
  /**
   * Constructs a {@link ReaderBucketState} for the given underlying {@link Bucket}.
   *
   * @param underlyingBucket The underlying {@link Bucket} to be shared among {@link ReaderBucket}
   *     instances.
   */
  public ReaderBucketState(Bucket underlyingBucket) {
    this.underlyingBucket = underlyingBucket;
  }

  /**
   * Increments the reference count when a new {@link ReaderBucket} is created. This method is
   * synchronized to ensure thread-safe incrementing of the counter.
   *
   * @throws IllegalStateException if the state is already closed, indicating that the underlying
   *     bucket is no longer available.
   * @see #refCount
   */
  public void addReference() {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("Bucket already closed");
      }
      refCount.incrementAndGet();
    }
  }

  /**
   * Decrements the reference count. When the count reaches zero, it disposes of the underlying
   * bucket. This method is synchronized to ensure thread-safe decrementing and disposal logic.
   *
   * <p>If the reference count becomes zero and the state is not already closed, this method sets
   * the {@link #closed} flag to true and calls {@link Bucket#dispose()} on the underlying bucket to
   * release its resources.
   *
   * @see #refCount
   * @see #underlyingBucket
   * @see #closed
   */
  public void release() {
    synchronized (lock) {
      if (refCount.decrementAndGet() == 0 && !closed) {
        closed = true;
        underlyingBucket.dispose();
      }
    }
  }

  /**
   * Gets an {@link InputStream} from the underlying bucket. This method checks if the state is
   * closed before obtaining the stream.
   *
   * @param buffered If {@code true}, a buffered {@link InputStream} is returned; otherwise, an
   *     unbuffered stream is returned.
   * @return An {@link InputStream} from the underlying bucket.
   * @throws IOException if the state is already closed.
   * @see #underlyingBucket
   * @see #closed
   */
  public InputStream getInputStream(boolean buffered) throws IOException {
    if (closed) {
      throw new IOException("Already closed");
    }
    return buffered
        ? underlyingBucket.getInputStream()
        : underlyingBucket.getInputStreamUnbuffered();
  }

  /**
   * Gets the name of the underlying bucket.
   *
   * @return The name of the underlying {@link Bucket}.
   * @see Bucket#getName()
   */
  public String getUnderlyingBucketName() {
    return underlyingBucket.getName();
  }

  /**
   * Gets the size of the underlying bucket.
   *
   * @return The size of the underlying {@link Bucket}.
   * @see Bucket#size()
   */
  public long getUnderlyingBucketSize() {
    return underlyingBucket.size();
  }

  /** The underlying {@link Bucket} being shared by {@link ReaderBucket} instances. */
  private final Bucket underlyingBucket;

  /** Reference count for the number of active {@link ReaderBucket} instances. */
  private final AtomicInteger refCount = new AtomicInteger(0);

  /**
   * Lock object used for synchronizing access to the shared state, including {@link #refCount} and
   * {@link #closed}.
   */
  private final transient Object lock = new Object();

  /**
   * Flag indicating whether the underlying bucket has been closed (disposed of). Once closed, no
   * new {@link InputStream} can be obtained, and further operations may throw exceptions.
   */
  private volatile boolean closed = false;
}
