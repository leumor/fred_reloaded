package hyphanet.support.io.storage.randomaccessbuffer;

import java.io.IOException;
import java.util.Arrays;

/**
 * A factory implementation that creates {@link RandomAccessBuffer} instances backed by byte
 * arrays. This implementation is suitable for in-memory operations and temporary storage.
 * <p>
 * The factory provides methods to create either empty buffers of a specified size or buffers
 * initialized with existing data. All buffers created by this factory are limited to arrays
 * that can fit in memory (maximum size of {@link Integer#MAX_VALUE}).
 * </p>
 *
 * @author toad
 * @see RandomAccessBuffer
 * @see Factory
 */
public class ByteArrayFactory implements Factory {

    /**
     * Creates a new {@link RandomAccessBuffer} with the specified size. The buffer is backed
     * by a newly allocated byte array.
     *
     * @param size The size of the buffer to create, must be non-negative and not larger than
     *             {@link Integer#MAX_VALUE}
     *
     * @return A new {@link RandomAccessBuffer} instance backed by a byte array
     *
     * @throws IllegalArgumentException if size is negative
     * @throws IOException              if the requested size exceeds
     *                                  {@link Integer#MAX_VALUE}
     */
    @Override
    public RandomAccessBuffer makeRab(long size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Buffer size cannot be negative: " + size);
        }

        if (size > Integer.MAX_VALUE) {
            throw new IOException("Requested size exceeds maximum allowed size: " + size);
        }

        return new ByteArray(new byte[(int) size]);
    }


    /**
     * Creates a new {@link RandomAccessBuffer} initialized with data from the provided byte
     * array. The data is copied from the specified range of the input array.
     * <p>
     * The resulting buffer can be created as read-only if required.
     * </p>
     *
     * @param initialContents the source array containing the initial data
     * @param offset          the starting position in the source array
     * @param size            the number of bytes to copy from the source array
     * @param readOnly        if true, the created buffer will be read-only
     *
     * @return A new {@link RandomAccessBuffer} instance containing the specified data
     *
     * @throws IllegalArgumentException if offset or size is negative, or if the specified
     *                                  range exceeds the bounds of the initial contents array
     * @throws IOException              if an I/O error occurs during buffer creation
     */
    @Override
    public RandomAccessBuffer makeRab(
        byte[] initialContents,
        int offset,
        int size,
        boolean readOnly
    ) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative: " + size);
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }

        if (offset + size > initialContents.length) {
            throw new IllegalArgumentException(
                "Requested range [offset=" + offset + ", size=" + size +
                "] exceeds array bounds [length=" + initialContents.length + "]");
        }

        return new ByteArray(
            Arrays.copyOfRange(initialContents, offset, offset + size),
                             0,
                             size,
                             readOnly
        );
    }

}
