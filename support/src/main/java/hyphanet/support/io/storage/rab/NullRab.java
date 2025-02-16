package hyphanet.support.io.storage.rab;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Null Object implementation of Random Access Buffer ({@link Rab}).
 *
 * <p>This class provides a no-op implementation of the {@link Rab} interface. It is useful for
 * scenarios where a {@link Rab} is required but actual storage operations are not needed, such as
 * testing or when dealing with optional storage components. All operations are effectively no-ops,
 * except for {@link #pread(long, byte[], int, int)} which fills the provided buffer with zeros and
 * {@link #size()} which returns the configured size. {@link #onResume(ResumeContext)} and {@link
 * #storeTo(DataOutputStream)} throw {@link UnsupportedOperationException}.
 */
public class NullRab implements Rab {

  /**
   * Constructs a {@link NullRab} with the specified size.
   *
   * @param size the size of the null buffer in bytes, as returned by {@link #size()}.
   */
  public NullRab(long size) {
    this.size = size;
  }

  /**
   * Returns the pre-configured size of this {@link NullRab}.
   *
   * <p>This method always returns the size provided in the constructor.
   *
   * @return the size of the null buffer in bytes.
   */
  @Override
  public long size() {
    return size;
  }

  /**
   * Fills the provided buffer with zeros, simulating a read operation.
   *
   * <p>This method does not actually read from any storage. Instead, it iterates through the
   * specified length and sets each byte in the destination buffer to zero. This simulates reading
   * zero-filled data from a {@link Rab}.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void pread(long fileOffset, byte[] buf, int bufOffset, int length) throws IOException {
    for (int i = 0; i < length; i++) {
      buf[bufOffset + i] = 0;
    }
  }

  /**
   * No-op implementation of write operation.
   *
   * <p>This method does nothing, effectively discarding any data written to it.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length) throws IOException {
    // Do nothing.
  }

  /**
   * No-op implementation of close operation.
   *
   * <p>This method does nothing as there are no resources to release in this null implementation.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void close() {
    // Do nothing.
  }

  /**
   * No-op implementation of dispose operation.
   *
   * <p>This method does nothing as there are no resources to dispose of in this null
   * implementation.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void dispose() {
    // Do nothing.
  }

  /**
   * Returns a {@link RabLock} that does nothing on unlock.
   *
   * <p>This method provides a no-op lock for compatibility with interfaces that require locking,
   * but locking is not relevant for this null implementation. The returned lock's {@link
   * RabLock#unlock()} method will do nothing.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public RabLock lockOpen() throws IOException {
    return new RabLock() {

      @Override
      protected void innerUnlock() {
        // Do nothing.
      }
    };
  }

  /**
   * Throws {@link UnsupportedOperationException} as resume is not supported for {@code NullRab}.
   *
   * <p>This implementation does not support resume operations, as it represents a null or in-memory
   * buffer that is not persisted.
   *
   * <p>{@inheritDoc}
   *
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    throw new UnsupportedOperationException();
  }

  /**
   * Throws {@link UnsupportedOperationException} as storing is not supported for {@code NullRab}.
   *
   * <p>This implementation does not support storing its state, as it represents a null or in-memory
   * buffer that is not intended to be persisted.
   *
   * <p>{@inheritDoc}
   *
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    return o.getClass() == getClass();
  }

  /** The pre-configured size of this {@code NullRab}. */
  private final long size;
}
