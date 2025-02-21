package hyphanet.support.io.storage;

import hyphanet.support.io.Resumable;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a generic resource for storing and accessing data. This interface is intended to be a
 * common parent for various data storage abstractions like Buckets and Random Access Buffers,
 * providing a unified way to manage data resources.
 */
public interface Storage extends Resumable, AutoCloseable {

  /** Default encryption type for encrypted storage. */
  EncryptType CRYPT_TYPE = EncryptType.CHACHA_128;

  /**
   * Returns the current size of data stored in this Storage.
   *
   * @return The size in bytes
   */
  long size();

  /**
   * Stores the Storage's reconstruction data to the provided output stream.
   *
   * <p>This method is intended for emergency recovery scenarios and may be version-dependent.
   *
   * @param dos The {@link DataOutputStream} to write to
   * @throws IOException if an I/O error occurs
   * @throws UnsupportedOperationException if the operation is not supported
   */
  void storeTo(DataOutputStream dos) throws IOException;

  /**
   * Closes this Storage and releases any system resources associated with it.
   *
   * <p>Once closed, further operations may throw an {@link IOException}.
   */
  @Override
  void close();

  /**
   * Releases the underlying resources and securely deletes data associated with this Storage.
   *
   * <p>This method may perform no operation in some implementations. Callers should ensure that the
   * object becomes eligible for garbage collection after calling this method.
   */
  void dispose();
}
