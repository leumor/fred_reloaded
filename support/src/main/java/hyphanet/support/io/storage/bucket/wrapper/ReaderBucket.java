package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.support.GlobalCleaner;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.NullBucket;
import java.io.*;
import java.lang.ref.Cleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A read-only {@link Bucket} wrapper that allows multiple concurrent readers to access the
 * underlying data. The underlying bucket's resources are released only when all {@link
 * ReaderBucket} instances referencing it are disposed of. This is achieved through reference
 * counting managed by {@link ReaderBucketState} and cleanup actions registered with {@link
 * GlobalCleaner}.
 *
 * <p>This class implements {@link Serializable} but is not intended to be persisted directly. It is
 * typically created by {@link ReaderBucketFactory} which is serializable.
 */
class ReaderBucket extends AbstractStorage implements Bucket {

  @Serial private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(ReaderBucket.class);

  /**
   * Constructs a {@link ReaderBucket} associated with the given {@link ReaderBucketState}. This
   * constructor increments the reference count in the provided state and registers a cleanup action
   * with {@link GlobalCleaner} to decrement the reference count and potentially dispose of the
   * underlying bucket when this {@link ReaderBucket} instance is no longer reachable.
   *
   * @param state The {@link ReaderBucketState} that manages the underlying bucket and reader count.
   */
  public ReaderBucket(ReaderBucketState state) {
    this.state = state;
    this.state.addReference();
    cleanable = GlobalCleaner.getInstance().register(this, new CleaningAction(state));
  }

  /**
   * Disposes of this {@link ReaderBucket}. This method triggers the cleanup action registered with
   * {@link GlobalCleaner}, which decrements the reference count in the associated {@link
   * ReaderBucketState}. If the reference count reaches zero, the underlying bucket is disposed of.
   *
   * <p>This method is idempotent and thread-safe.
   *
   * @see CleaningAction#run()
   */
  @Override
  public void dispose() {
    if (!setDisposed()) {
      return;
    }
    cleanable.clean();
  }

  /**
   * {@inheritDoc}
   *
   * @return An {@link InputStream} for reading from the underlying bucket, buffered.
   * @throws IOException if the {@link ReaderBucket} has been disposed of.
   * @see ReaderBucketInputStream
   */
  @Override
  public InputStream getInputStream() throws IOException {
    checkDisposed();
    return new ReaderBucketInputStream(true);
  }

  /**
   * {@inheritDoc}
   *
   * @return An unbuffered {@link InputStream} for reading from the underlying bucket.
   * @throws IOException if the {@link ReaderBucket} has been disposed of.
   * @see ReaderBucketInputStream
   */
  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    checkDisposed();
    return new ReaderBucketInputStream(false);
  }

  /**
   * {@inheritDoc}
   *
   * @return The name of the underlying bucket.
   */
  @Override
  public String getName() {
    return state.getUnderlyingBucketName();
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException always, as this is a read-only bucket.
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    throw new IOException("Read only");
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException always, as this is a read-only bucket.
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    throw new IOException("Read only");
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code true} always, as this is a read-only bucket.
   */
  @Override
  public boolean isReadOnly() {
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Does nothing as this bucket is already read-only.
   */
  @Override
  public void setReadOnly() {
    // Already read only
  }

  /**
   * {@inheritDoc}
   *
   * @return The size of the underlying bucket.
   */
  @Override
  public long size() {
    return state.getUnderlyingBucketSize();
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code null} as shadow buckets are not supported for {@link ReaderBucket}.
   */
  @Override
  public Bucket createShadow() {
    return new NullBucket();
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always, as {@link ReaderBucket} is not persistent.
   */
  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    throw new UnsupportedOperationException(); // Not persistent.
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always, as {@link ReaderBucket} is not persistent.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Checks if this {@link ReaderBucket} has been disposed of.
   *
   * @throws IOException if the bucket is already disposed.
   */
  private synchronized void checkDisposed() throws IOException {
    if (disposed()) {
      throw new IOException("Already disposed");
    }
  }

  /**
   * A {@link Runnable} action executed by {@link Cleaner} when a {@link ReaderBucket} is garbage
   * collected. This action releases a reference to the underlying bucket via {@link
   * ReaderBucketState#release()}.
   *
   * @see GlobalCleaner
   */
  static class CleaningAction implements Runnable {
    /**
     * Constructs a {@link CleaningAction} for the given {@link ReaderBucketState}.
     *
     * @param state The {@link ReaderBucketState} to release a reference from.
     */
    CleaningAction(ReaderBucketState state) {
      this.state = state;
    }

    /**
     * Releases a reference to the underlying bucket by calling {@link ReaderBucketState#release()}.
     * This method is invoked by the {@link Cleaner} when the associated {@link ReaderBucket} is
     * garbage collected.
     */
    @Override
    public void run() {
      state.release();
      logger.info("Cleaner run.");
    }

    /** The {@link ReaderBucketState} to release a reference from. */
    private final ReaderBucketState state;
  }

  /**
   * An inner class implementing {@link InputStream} for reading from a {@link ReaderBucket}. It
   * delegates read operations to an {@link InputStream} obtained from the underlying bucket via the
   * associated {@link ReaderBucketState}.
   *
   * <p>This class ensures that operations are performed on the underlying stream only if the {@link
   * ReaderBucket} has not been disposed of.
   */
  private class ReaderBucketInputStream extends InputStream {

    /**
     * Constructs a {@link ReaderBucketInputStream}.
     *
     * @param buffer If {@code true}, a buffered {@link InputStream} is obtained from the {@link
     *     ReaderBucketState}; otherwise, an unbuffered stream is obtained.
     * @throws IOException If an I/O error occurs while obtaining the {@link InputStream} from the
     *     state.
     */
    ReaderBucketInputStream(boolean buffer) throws IOException {
      is = state.getInputStream(buffer);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the {@link ReaderBucket} has been disposed of or if an I/O error
     *     occurs during the read operation on the underlying stream.
     */
    @Override
    public final int read() throws IOException {
      checkDisposed();
      return is.read();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the {@link ReaderBucket} has been disposed of or if an I/O error
     *     occurs during the read operation on the underlying stream.
     */
    @Override
    public final int read(byte[] data, int offset, int length) throws IOException {
      checkDisposed();
      return is.read(data, offset, length);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the {@link ReaderBucket} has been disposed of or if an I/O error
     *     occurs during the read operation on the underlying stream.
     */
    @Override
    public final int read(byte[] data) throws IOException {
      checkDisposed();
      return is.read(data);
    }

    @Override
    public final void close() throws IOException {
      is.close();
    }

    @Override
    public final int available() throws IOException {
      return is.available();
    }

    /**
     * The underlying {@link InputStream} obtained from the {@link ReaderBucketState}. This stream
     * is used for all read operations in {@link ReaderBucketInputStream}.
     */
    InputStream is;
  }

  /**
   * The shared state associated with this {@link ReaderBucket}. It manages the underlying bucket
   * and the reference count of readers.
   */
  private final ReaderBucketState state;

  /**
   * A {@link Cleaner.Cleanable} instance registered with {@link GlobalCleaner}. This is used to
   * schedule the {@link CleaningAction} to be executed when this {@link ReaderBucket} is no longer
   * reachable, ensuring resources are released even if {@link #dispose()} is not explicitly called.
   */
  private final transient Cleaner.Cleanable cleanable;
}
