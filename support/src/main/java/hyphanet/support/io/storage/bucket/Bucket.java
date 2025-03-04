/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.Storage;
import hyphanet.support.io.stream.NullInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Represents a temporary data storage container that can hold arbitrary data. A Bucket is
 * conceptually similar to a temporary file but may be implemented using various storage mechanisms
 * including RAM, disk storage, encryption, or combinations thereof.
 *
 * <p>Buckets provide a unified interface for temporary data storage regardless of the underlying
 * implementation. They can be chained, encrypted, or stored in various ways while maintaining a
 * consistent API.
 *
 * <p><strong>Note:</strong> Not all Bucket implementations are Serializable.
 *
 * @author oskar
 */
public interface Bucket extends Storage {

  /**
   * Creates a new {@link OutputStream} for writing data to this Bucket from the beginning.
   *
   * <p><strong>Important:</strong> Appending data is not supported. This simplifies the code
   * significantly for some classes. If you need to append, just pass the OutputStream around. The
   * stream will be automatically buffered if appropriate for the implementation.
   *
   * @return A new {@link OutputStream} for writing to this Bucket
   * @throws IOException if an I/O error occurs
   */
  OutputStream getOutputStream() throws IOException;

  /**
   * Creates an unbuffered {@link OutputStream} for writing data to this Bucket.
   *
   * <p>This method should be used when:
   *
   * <ul>
   *   <li>Buffering is handled at a higher level
   *   <li>Only large writes will be performed (e.g., copying between Buckets)
   * </ul>
   *
   * @return An unbuffered {@link OutputStream}
   * @throws IOException if an I/O error occurs
   */
  OutputStream getOutputStreamUnbuffered() throws IOException;

  /**
   * Creates an {@link InputStream} to read data from this Bucket.
   *
   * <p><strong>Resource Management:</strong> The caller must close the returned stream to prevent
   * resource leaks.
   *
   * @return An {@link InputStream}, or {@link NullInputStream} if the Bucket is empty
   * @throws IOException if an I/O error occurs
   */
  InputStream getInputStream() throws IOException;

  /**
   * Creates an unbuffered {@link InputStream} to read data from this Bucket.
   *
   * <p><strong>Resource Management:</strong> The caller must close the returned stream to prevent
   * resource leaks.
   *
   * @return An unbuffered {@link InputStream}, or {@link NullInputStream} if the Bucket is empty
   * @throws IOException if an I/O error occurs
   */
  InputStream getInputStreamUnbuffered() throws IOException;

  /**
   * Returns an identifier for this Bucket.
   *
   * @return A String name identifying this Bucket
   */
  String getName();

  /**
   * Returns the current size of data stored in this Bucket.
   *
   * @return The size in bytes
   */
  @Override
  long size();

  /**
   * Checks if this Bucket is read-only.
   *
   * @return {@code true} if the Bucket is read-only, {@code false} otherwise
   */
  boolean isReadOnly();

  /** Makes this Bucket read-only. This operation cannot be reversed. */
  void setReadOnly();

  /**
   * Creates a shallow, read-only copy of this Bucket sharing the same external storage.
   *
   * <p><strong>Warning:</strong> If the original Bucket is deleted, the shadow copy may become
   * invalid, potentially throwing {@link IOException} on read operations or returning incomplete
   * data.
   *
   * @return A new Bucket instance, or {@link NullBucket} if shadow creation is not supported
   */
  Bucket createShadow();

  /**
   * Called after restarting. The Bucket should do any necessary housekeeping after resuming, e.g.
   * registering itself with the appropriate persistent bucket tracker to avoid being
   * garbage-collected. May be called twice, so the Bucket may need to track this internally.
   *
   * @param context The necessary runtime support for resuming the Bucket. This is provided through
   *     an implementation of the {@link ResumeContext} interface.
   * @throws ResumeFailedException If the resumption process encounters an error and the Bucket
   *     cannot be properly initialized.
   * @see ResumeContext
   */
  @Override
  default void onResume(ResumeContext context) throws ResumeFailedException {
    // Do nothing
  }

  /**
   * Stores the Bucket's reconstruction data to the provided output stream.
   *
   * <p>This method is intended for emergency recovery scenarios and may be version-dependent.
   *
   * @param dos The {@link DataOutputStream} to write to
   * @throws IOException if an I/O error occurs
   * @throws UnsupportedOperationException if the operation is not supported
   */
  @Override
  void storeTo(DataOutputStream dos) throws IOException;

  /**
   * Closes this bucket and releases any system resources associated with it.
   *
   * <p>Once closed, further read or write operations will throw an {@link IOException}.
   */
  @Override
  void close();

  /**
   * Releases the underlying resources and securely deletes data associated with this bucket.
   *
   * <p>This method may perform no operation in some implementations. Callers should ensure that the
   * object becomes eligible for garbage collection after calling this method.
   */
  @Override
  default void dispose() {
    close();
  }
}
