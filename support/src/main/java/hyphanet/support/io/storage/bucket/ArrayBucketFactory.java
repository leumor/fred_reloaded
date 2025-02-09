/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import java.io.IOException;

/**
 * A factory implementation that creates {@link ArrayBucket} bucket instances. This factory is
 * responsible for creating memory-based buckets that store data in byte arrays.
 *
 * <p>This implementation is part of the bucket factory system and provides
 * a simple way to create in-memory buckets for temporary data storage.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * ArrayFactory factory = new ArrayFactory();
 * RandomAccessable bucket = factory.makeBucket(1024); // Creates a bucket with 1024 bytes size hint
 * </pre>
 *
 * @see BucketFactory
 * @see ArrayBucket
 * @see RandomAccessible
 */
public class ArrayBucketFactory implements BucketFactory {

    /**
     * Creates a new {@link ArrayBucket} bucket instance.
     *
     * <p>The size parameter is not used. The created Array bucket will actually grow
     * dynamically as needed.</p>
     *
     * @param size Not used in this implementation
     *
     * @return A new {@link RandomAccessible} bucket instance
     *
     * @throws IOException If there is an error creating the bucket
     */
    @Override
    public RandomAccessible makeBucket(long size) throws IOException {
        return new ArrayBucket();
    }

    /**
     * Disposes of a bucket, releasing any resources it holds.
     *
     * <p>This method ensures proper cleanup of bucket resources
     * when they are no longer needed.</p>
     *
     * @param b The bucket to dispose
     *
     * @throws IOException If there is an error during disposal
     */
    public void disposeBucket(Bucket b) throws IOException {
        b.dispose();
    }

}
