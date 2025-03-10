package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.storage.rab.Rab;

import java.io.IOException;

/**
 * A specialized {@link Bucket} implementation that supports random access operations. This
 * interface provides functionality to convert a Bucket to a {@link Rab} random access buffer
 * without data copying.
 *
 * <p>This interface is particularly useful when dealing with data of uncertain size that will later
 * need random access capabilities. It provides a separate abstraction due to API incompatibilities,
 * particularly regarding size constraints - a RandomAccessBuffer has a fixed size, which is a
 * design feature.
 *
 * <h3>Finalization Behavior:</h3>
 *
 * <ul>
 *   <li>Persistent RandomAccess bucket implementations must never free resources in finalizers
 *   <li>Transient RandomAccess bucket implementations may free resources in finalizers, but must
 *       ensure this only occurs when both the {@link Bucket} and {@link Rab} are unreachable
 * </ul>
 *
 * @see Bucket
 * @see Rab
 * @see Rab
 */
public interface RandomAccessBucket extends Bucket {

  /**
   * Converts this Bucket to a {@link Rab} random access buffer efficiently without copying the
   * underlying data.
   *
   * <p>After conversion:
   *
   * <ul>
   *   <li>Both the original Bucket and the returned buffer become read-only
   *   <li>Freeing the original Bucket becomes optional if the returned buffer is freed
   * </ul>
   *
   * @return A {@link Rab} random access buffer containing this bucket's data
   * @throws IOException if the conversion fails due to I/O errors
   */
  Rab toRandomAccessBuffer() throws IOException;

  /**
   * {@inheritDoc} Creates a read-only shadow copy of this RandomAccess bucket.
   *
   * @return A new RandomAccess instance sharing the same underlying storage
   */
  @Override
  RandomAccessBucket createShadow();
}
