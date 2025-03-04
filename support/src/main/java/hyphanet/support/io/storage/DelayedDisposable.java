package hyphanet.support.io.storage;

/**
 * Represents a delayed disposal mechanism for Buckets or RandomAccessBuffers.
 *
 * <p>This interface defines methods to handle delayed disposal of resources, ensuring that actual
 * disposal occurs only after client.dat file is written. This prevents premature resource cleanup
 * while maintaining proper resource management.
 */
public interface DelayedDisposable {

  /**
   * Checks if this resource is marked for disposal.
   *
   * @return {@code true} if the resource is marked for disposal, {@code false} otherwise
   */
  boolean toDispose();

  /**
   * Performs the actual disposal of the resource.
   *
   * <p>This method should be called only after ensuring that all necessary data has been written to
   * persistent storage.
   */
  void realDispose();
}
