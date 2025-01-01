package hyphanet.support.io.bucket;

import hyphanet.support.io.randomaccessbuffer.RandomAccessBuffer;

import java.io.IOException;

/**
 * A specialized {@link Bucket} implementation that supports random access operations. This
 * interface provides functionality to convert a Bucket to a {@link RandomAccessBuffer} random
 * access buffer without data copying.
 *
 * <p>This interface is particularly useful when dealing with data of uncertain size
 * that will later need random access capabilities. It provides a separate abstraction due to
 * API incompatibilities, particularly regarding size constraints - a RandomAccessBuffer has a
 * fixed size, which is a design feature.</p>
 *
 * <h3>Finalization Behavior:</h3>
 * <ul>
 *   <li>Persistent RandomAccess bucket implementations must never free resources in
 *   finalizers</li>
 *   <li>Transient RandomAccess bucket implementations may free resources in finalizers, but
 *   must ensure this only occurs when both the {@link Bucket} and
 *   {@link RandomAccessBuffer} are unreachable</li>
 * </ul>
 *
 * @see Bucket
 * @see RandomAccessBuffer
 * @see RandomAccessBuffer
 */
public interface RandomAccess extends Bucket {

    /**
     * Converts this Bucket to a {@link RandomAccessBuffer} random access buffer efficiently
     * without copying the underlying data.
     *
     * <p>After conversion:</p>
     * <ul>
     *   <li>Both the original Bucket and the returned buffer become read-only</li>
     *   <li>Freeing the original Bucket becomes optional if the returned buffer is freed</li>
     * </ul>
     *
     * @return A {@link RandomAccessBuffer} random access buffer containing this bucket's data
     *
     * @throws IOException if the conversion fails due to I/O errors
     */
    RandomAccessBuffer toRandomAccessBuffer() throws IOException;

    /**
     * {@inheritDoc} Creates a read-only shadow copy of this RandomAccess bucket.
     *
     * @return A new RandomAccess instance sharing the same underlying storage
     */
    @Override
    RandomAccess createShadow();

}
