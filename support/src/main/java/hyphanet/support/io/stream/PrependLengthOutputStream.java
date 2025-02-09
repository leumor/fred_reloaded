package hyphanet.support.io.stream;

import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import hyphanet.support.io.storage.bucket.BucketTools;

import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} that writes data to a temporary {@link Bucket} first. Upon {@link
 * #close()} (unless {@link #abort()} is called), it prepends the length of the written data (minus
 * a specified offset) to the output stream and then copies the data from the temporary {@link
 * Bucket}. This is useful when the length of the data to be written is not known in advance, but
 * needs to be prepended to the output stream before the data itself.
 *
 * <p>The underlying {@link Bucket} is used as temporary storage and is disposed of when this stream
 * is closed. The underlying output stream passed to the constructor is not closed by this class
 * unless specified via the {@code closeUnderlying} parameter in the {@link #create(OutputStream,
 * BucketFactory, int, boolean)} method.
 */
public class PrependLengthOutputStream extends FilterOutputStream {

  /**
   * Constructs a {@code PrependLengthOutputStream}.
   *
   * <p>This constructor is private. Instances should be created using the static {@link
   * #create(OutputStream, BucketFactory, int, boolean)} method.
   *
   * @param os The {@link OutputStream} to write to for the temporary {@link Bucket}. This is
   *     obtained from the temporary {@link Bucket}.
   * @param temp The temporary {@link Bucket} used for buffering the data before writing to the
   *     original {@link OutputStream}.
   * @param origOS The original {@link OutputStream} to which the length and data will be written
   *     upon closing.
   * @param offset The offset to subtract from the final size when writing the length. This is
   *     useful for excluding headers or other prepended data that shouldn't be counted in the
   *     length.
   * @param closeUnderlying Whether to close the underlying {@code origOS} when this stream is
   *     closed.
   */
  private PrependLengthOutputStream(
      OutputStream os, Bucket temp, OutputStream origOS, int offset, boolean closeUnderlying) {
    super(os);
    this.temp = temp;
    this.origOS = origOS;
    this.offset = offset;
    this.closeUnderlying = closeUnderlying;
  }

  /**
   * Creates a new {@link PrependLengthOutputStream}.
   *
   * <p>This is the factory method for creating instances of {@link PrependLengthOutputStream}. It
   * creates a temporary {@link Bucket} using the provided {@link BucketFactory} and obtains an {@link
   * OutputStream} from it to buffer the data.
   *
   * @param out The original {@link OutputStream} to which the length and data will be written upon
   *     closing.
   * @param bf The {@link BucketFactory} used to create the temporary {@link Bucket}.
   * @param offset The offset to subtract from the final size when writing the length. This allows
   *     for prepending data to the temporary bucket that should not be included in the length.
   * @param closeUnderlying Whether to close the underlying {@code out} {@link OutputStream} when
   *     this stream is closed. If {@code true}, the {@code out} stream will be closed; otherwise,
   *     it will remain open after {@link #close()}.
   * @return A new {@code PrependLengthOutputStream} instance.
   * @throws IOException If an I/O error occurs, such as if creating the temporary {@link Bucket}
   *     fails or if obtaining an {@link OutputStream} from it fails.
   */
  public static PrependLengthOutputStream create(
      OutputStream out, BucketFactory bf, int offset, boolean closeUnderlying) throws IOException {
    Bucket temp = bf.makeBucket(-1);
    OutputStream os = temp.getOutputStream();
    return new PrependLengthOutputStream(os, temp, out, offset, closeUnderlying);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link FilterOutputStream#write(byte[], int, int)} to ensure that writes are also
   * passed to the underlying output stream {@code out}, even though {@link FilterOutputStream}
   * might optimize by calling {@link #write(int)} repeatedly. This is necessary because the
   * underlying {@code out} in this class is actually writing to a temporary {@link Bucket}.
   */
  @Override
  public void write(byte[] buf, int offset, int length) throws IOException {
    // Unfortunately this is necessary because FilterOutputStream passes everything
    // through write(int).
    out.write(buf, offset, length);
  }

  /**
   * Aborts the stream operation.
   *
   * <p>If called before {@link #close()}, when {@code close()} is invoked, it will write a length
   * of 0 to the underlying output stream, effectively discarding the data written to the temporary
   * {@link Bucket}.
   *
   * @return {@code true} if the stream was successfully aborted (i.e., it was not already closed),
   *     {@code false} otherwise. If the stream is already closed, this method returns {@code false}
   *     and does not change the aborted state.
   */
  public boolean abort() {
    if (closed) {
      return false;
    }
    aborted = true;
    return true;
  }

  /**
   * Closes this output stream.
   *
   * <p>This method performs the following actions:
   *
   * <ol>
   *   <li>Closes the output stream associated with the temporary {@link Bucket} to finalize writing
   *       to the temporary storage.
   *   <li>Creates a {@link DataOutputStream} on top of the original output stream ({@link
   *       #origOS}).
   *   <li>Writes the length of the data to {@link #origOS} as a {@code long}. If {@link #abort()}
   *       has been called, it writes 0 as the length. Otherwise, it calculates the length by
   *       getting the size of the temporary {@link Bucket} and subtracting the {@code offset}.
   *   <li>If not aborted, copies the data from the temporary {@link Bucket} to {@link #origOS}
   *       using {@link BucketTools#copyTo(Bucket, OutputStream, long)}.
   *   <li>Disposes of the temporary {@link Bucket} to release resources.
   *   <li>If {@link #closeUnderlying} is {@code true}, closes the original output stream ({@link
   *       #origOS}).
   * </ol>
   *
   * <p>Any {@link IOException} encountered during these operations is caught and re-thrown at the
   * end. If multiple exceptions occur, the first one encountered is thrown.
   *
   * @throws IOException If an I/O error occurs during any of the close operations, length writing,
   *     data copying, or closing of the underlying stream.
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;

    IOException thrown = null;
    try {
      out.close();
    } catch (IOException e) {
      thrown = e;
    }
    DataOutputStream dos = new DataOutputStream(origOS);
    try {
      if (aborted) {
        dos.writeLong(0);
      } else {
        dos.writeLong(temp.size() - offset);
        BucketTools.copyTo(temp, dos, Long.MAX_VALUE);
      }
    } catch (IOException e) {
      if (thrown == null) {
        thrown = e;
      }
    } finally {
      // Ensure resources are disposed/closed
      temp.dispose();
      if (closeUnderlying) {
        try {
          dos.close();
        } catch (IOException e) {
          if (thrown == null) {
            thrown = e;
          }
        }
      }
    }

    if (thrown != null) {
      throw thrown;
    }
  }

  /**
   * The temporary {@link Bucket} used to buffer the data before writing to the original output
   * stream. This bucket is created in the {@link #create(OutputStream, BucketFactory, int, boolean)}
   * method and disposed of in the {@link #close()} method.
   */
  private final Bucket temp;

  /**
   * The original {@link OutputStream} to which the prepended length and data from the temporary
   * {@link Bucket} are written upon {@link #close()}. This stream is passed to the {@link
   * #create(OutputStream, BucketFactory, int, boolean)} method.
   */
  private final OutputStream origOS;

  /**
   * The offset to subtract from the size of the temporary {@link Bucket} when writing the length to
   * the {@link #origOS}. This is useful for excluding any prepended data in the temporary bucket
   * that should not be included in the final length.
   */
  private final int offset;

  /**
   * A flag indicating whether the underlying {@link #origOS} should be closed when this {@link
   * PrependLengthOutputStream} is closed. This value is set in the {@link #create(OutputStream,
   * BucketFactory, int, boolean)} method.
   */
  private final boolean closeUnderlying;

  /**
   * A flag indicating whether the stream operation has been aborted by calling {@link #abort()}. If
   * {@code true}, {@link #close()} will write a length of 0 and not copy data from the temporary
   * {@link Bucket}.
   */
  private boolean aborted;

  /**
   * A flag indicating whether this stream has been closed. Prevents double closing and ensures that
   * {@link #close()} operations are idempotent.
   */
  private boolean closed;
}
