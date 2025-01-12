/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.bucket;

import java.io.IOException;


/**
 * A factory interface for creating bucket instances in the I/O system.
 *
 * <p>This interface defines the contract for creating bucket objects that can
 * store data. Different implementations of this interface can provide various storage
 * mechanisms (e.g., memory-based, file-based, etc.).</p>
 *
 * <p>All Factory implementations should ensure proper resource management and
 * handle creation errors appropriately.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Factory factory = new ArrayFactory();
 * RandomAccessable bucket = factory.makeBucket(1024);
 * </pre>
 *
 * @see Bucket
 * @see RandomAccessible
 */
public interface Factory {
    /**
     * Creates a new bucket instance with the specified size hint.
     *
     * <p>The size parameter serves as a hint for the expected maximum data size.
     * Some implementations may enforce this limit strictly, while others might allow exceeding
     * it. When the size is unknown, use -1 or {@link Long#MAX_VALUE}.</p>
     *
     * @param size The suggested maximum size of the data in bytes. Use -1 or
     *             {@link Long#MAX_VALUE} if the size is unknown
     *
     * @return A new {@link RandomAccessible} bucket instance
     *
     * @throws IOException If there is an error creating the bucket or if the requested size
     *                     cannot be accommodated
     */
    RandomAccessible makeBucket(long size) throws IOException;

}

