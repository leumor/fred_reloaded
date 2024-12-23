package hyphanet.support.io.bucket;

import hyphanet.support.io.randomaccessbuffer.Lockable;

import java.io.IOException;


/**
 * A Bucket which can be converted to a LockableRandomAccessBuffer without copying. Mostly we
 * need this where the size of something we will later use as a RandomAccessBuffer is
 * uncertain. It provides a separate object because the API's are incompatible; in particular,
 * the size of a RandomAccessBuffer is fixed (and this is mostly a good thing).
 * <p>
 * FINALIZERS: Persistent RandomAccessBucket's should never free on finalize. Transient RABs
 * can free on finalize, but must ensure that this only happens if both the Bucket and the RAB
 * are no longer reachable.
 */
public interface RandomAccess extends Bucket {

    /**
     * Convert the Bucket to a LockableRandomAccessBuffer. Must be efficient, i.e. will not
     * copy the data. Freeing the Bucket is unnecessary if you free the
     * LockableRandomAccessBuffer. Both the parent Bucket and the return value will be made
     * read only.
     *
     * @throws IOException
     */
    Lockable toRandomAccessBuffer() throws IOException;

    @Override
    RandomAccess createShadow();

}
