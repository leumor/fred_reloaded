/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.rab.NullRab;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.stream.NullInputStream;
import hyphanet.support.io.stream.NullOutputStream;
import java.io.*;

/**
 * A {@link Bucket} implementation that does nothing.
 *
 * <p>This bucket is primarily used for testing or in situations where a bucket is required but no
 * actual storage is needed. All write operations are discarded, and read operations return empty
 * streams.
 *
 * <p>This class is {@link Serializable}.
 */
public class NullBucket implements RandomAccessBucket, Serializable {

  /** A static, shared {@link NullOutputStream} instance for all {@link NullBucket} writes. */
  public static final OutputStream nullOut = new NullOutputStream();

  /** A static, shared {@link NullInputStream} instance for all {@link NullBucket} reads. */
  public static final InputStream nullIn = new NullInputStream();

  @Serial private static final long serialVersionUID = 1L;

  /** Constructs a {@link NullBucket} with a default size of 0. */
  public NullBucket() {
    this(0);
  }

  /**
   * Constructs a {@link NullBucket} with a specified size.
   *
   * @param size The size of the {@link NullBucket}. This determines the size reported by {@link
   *     #size()} and the number of null bytes returned by read operations.
   */
  public NullBucket(long size) {
    this.size = size;
  }

  /**
   * Returns an {@link OutputStream} that discards all written data.
   *
   * @return A shared {@link NullOutputStream} instance ({@link #nullOut}).
   */
  @Override
  public OutputStream getOutputStream() {
    return nullOut;
  }

  /**
   * Returns an unbuffered {@link OutputStream} that discards all written data.
   *
   * @return A shared {@link NullOutputStream} instance ({@link #nullOut}).
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() {
    return nullOut;
  }

  /**
   * Returns an {@link InputStream} that provides no data or a stream of null bytes depending on the
   * bucket's size.
   *
   * @return A shared {@link NullInputStream} instance ({@link #nullIn}).
   */
  @Override
  public InputStream getInputStream() {
    return nullIn;
  }

  /**
   * Returns an unbuffered {@link InputStream} that provides no data.
   *
   * @return A shared {@link NullInputStream} instance ({@link #nullIn}).
   */
  @Override
  public InputStream getInputStreamUnbuffered() {
    return nullIn;
  }

  /**
   * Returns the pre-defined size of this {@link NullBucket}.
   *
   * @return The size specified in the constructor.
   */
  @Override
  public long size() {
    return size;
  }

  /**
   * Returns a fixed, humorous name for this {@link NullBucket}.
   *
   * @return The string "President George W. NullBucket".
   */
  @Override
  public String getName() {
    return "President George W. NullBucket";
  }

  /**
   * Always returns {@code false} as {@link NullBucket} is always considered read-write.
   *
   * @return {@code false}
   */
  @Override
  public boolean isReadOnly() {
    return false;
  }

  /** Does nothing. {@link NullBucket} is always read-write, and this method has no effect. */
  @Override
  public void setReadOnly() {
    // Do nothing
  }

  /**
   * Does nothing. No resources are held by a {@link NullBucket}.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void dispose() {
    // Do nothing
  }

  /**
   * Creates a new {@link NullBucket} instance as a shadow copy.
   *
   * @return A new {@link NullBucket} with the same size.
   */
  @Override
  public RandomAccessBucket createShadow() {
    return new NullBucket(size);
  }

  /**
   * Does nothing. No resumption actions are needed for a {@link NullBucket}.
   *
   * @param context The resume context (ignored).
   */
  @Override
  public void onResume(ResumeContext context) {
    // Do nothing.
  }

  /**
   * Throws {@link UnsupportedOperationException}. {@link NullBucket} cannot be stored.
   *
   * @param dos The {@link DataOutputStream} (ignored).
   * @throws UnsupportedOperationException Always.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Does nothing. No resources are held by a {@link NullBucket}.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void close() {
    // Do nothing
  }

  /**
   * Returns a {@link NullRab} of the same size as this {@link NullBucket}.
   *
   * @return A new {@link NullRab} instance.
   * @throws IOException Never, as {@link NullRab} creation does not throw {@link IOException}.
   */
  @Override
  public Rab toRandomAccessBuffer() throws IOException {
    return new NullRab(size);
  }

  /** The pre-defined size of this {@link NullBucket}. Reads will return this many null bytes. */
  private final long size;
}
