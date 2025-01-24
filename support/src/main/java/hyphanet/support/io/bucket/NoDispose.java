package hyphanet.support.io.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.StorageFormatException;
import java.io.*;

/**
 * A wrapper class for {@link Bucket} implementations that prevents the disposal of the underlying
 * bucket. This class acts as a proxy, forwarding all {@link Bucket} operations to the wrapped
 * bucket, except for the {@link #dispose()} method, which is intentionally made to do nothing.
 *
 * <p>This is useful in scenarios where a {@link Bucket} needs to be passed around or used in a
 * context where disposal is automatically invoked but should be prevented, for instance, when the
 * lifecycle of the underlying bucket is managed elsewhere.
 *
 * <p><b>Serialization:</b> This class is serializable, allowing instances to be persisted and
 * restored. Upon deserialization, it attempts to restore the proxied {@link Bucket} as well.
 *
 * @see Bucket
 */
public class NoDispose implements Bucket, Serializable {

  /** Magic number used for serialization to verify the class type during deserialization. */
  static final int MAGIC = 0xa88da5c2;

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs a new {@code NoDispose} bucket that wraps the given {@code Bucket}.
   *
   * @param orig The original {@link Bucket} to be wrapped and whose disposal should be prevented.
   *     All operations on the {@code NoDispose} instance will be forwarded to this bucket.
   */
  public NoDispose(Bucket orig) {
    proxy = orig;
  }

  /**
   * Default constructor for serialization purposes.
   *
   * <p>This constructor is intentionally protected and parameterless to be accessible during
   * deserialization. It initializes the {@link #proxy} to {@code null}, which should be restored
   * during the deserialization process using constructor {@link #NoDispose(DataInputStream,
   * FilenameGenerator, PersistentFileTracker, MasterSecret)}.
   */
  protected NoDispose() {
    // For serialization.
    proxy = null;
  }

  /**
   * Constructs a {@code NoDispose} bucket by restoring its state from a {@link DataInputStream}.
   *
   * <p>This constructor is used during deserialization to reconstruct a {@code NoDispose} instance
   * from a serialized stream. It reads the necessary data from the input stream to restore the
   * proxied {@link Bucket}.
   *
   * @param dis The {@link DataInputStream} to read the serialized state from.
   * @param fg The {@link FilenameGenerator} to be used for bucket restoration.
   * @param persistentFileTracker The {@link PersistentFileTracker} for persistent file management.
   * @param masterKey The {@link MasterSecret} for decryption if the bucket is encrypted.
   * @throws IOException If an I/O error occurs during the restoration process.
   * @throws StorageFormatException If the serialized data is in an invalid format.
   * @throws ResumeFailedException If the bucket restoration fails for any reason.
   * @see BucketTools#restoreFrom(DataInputStream, FilenameGenerator, PersistentFileTracker,
   *     MasterSecret)
   */
  protected NoDispose(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws IOException, StorageFormatException, ResumeFailedException {
    proxy = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return proxy.getOutputStream();
  }

  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    return proxy.getOutputStreamUnbuffered();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return proxy.getInputStream();
  }

  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return proxy.getInputStreamUnbuffered();
  }

  @Override
  public String getName() {
    return proxy.getName();
  }

  @Override
  public long size() {
    return proxy.size();
  }

  @Override
  public boolean isReadOnly() {
    return proxy.isReadOnly();
  }

  @Override
  public void setReadOnly() {
    proxy.setReadOnly();
  }

  /**
   * {@inheritDoc}
   *
   * <p><b>Implementation Note:</b> This method intentionally does nothing to prevent the disposal
   * of the underlying bucket. This is the core purpose of the {@code NoDispose} class.
   */
  @Override
  public void dispose() {
    // Do nothing.
  }

  @Override
  public void close() {
    proxy.close();
  }

  @Override
  public Bucket createShadow() {
    return proxy.createShadow();
  }

  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    proxy.onResume(context);
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    proxy.storeTo(dos);
  }

  /**
   * The underlying {@link Bucket} instance that this class proxies. All {@link Bucket} operations,
   * except {@link #dispose()}, are delegated to this proxy.
   */
  final Bucket proxy;
}
