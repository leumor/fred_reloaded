package hyphanet.support.io.bucket;

import hyphanet.support.io.randomaccessbuffer.ByteArrayRandomAccessBuffer;
import hyphanet.support.io.randomaccessbuffer.Lockable;

import java.io.*;
import java.util.Arrays;

/**
 * A bucket that stores data in the memory.
 * <p>
 * FIXME: No synchronization, should there be?
 *
 * @author oskar
 */
public class Array implements Bucket, Serializable, RandomAccess {
    private static final long serialVersionUID = 1L;

    public Array() {
        this("ArrayBucket");
    }

    public Array(byte[] initdata) {
        this("ArrayBucket");
        data = initdata;
    }

    public Array(String name) {
        data = new byte[0];
        this.name = name;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (readOnly) {
            throw new IOException("Read only");
        }
        if (freed) {
            throw new IOException("Already freed");
        }
        return new ArrayBucketOutputStream();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (freed) {
            throw new IOException("Already freed");
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public String toString() {
        return new String(data);
    }

    @Override
    public long size() {
        return data.length;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void setReadOnly() {
        readOnly = true;
    }

    @Override
    public void free() {
        freed = true;
        data = null;
        // Not much else we can do.
    }

    public byte[] toByteArray() throws IOException {
        if (freed) {
            throw new IOException("Already freed");
        }
        long sz = size();
        int size = (int) sz;
        return Arrays.copyOf(data, size);
    }

    @Override
    public RandomAccess createShadow() {
        return null;
    }

    // TODO
    //    @Override
    //    public void onResume(ClientContext context) {
    //        // Do nothing.
    //    }

    @Override
    public void storeTo(DataOutputStream dos) {
        // Should not be used for persistent requests.
        throw new UnsupportedOperationException();
    }

    @Override
    public Lockable toRandomAccessBuffer() {
        readOnly = true;
        Lockable raf = new ByteArrayRandomAccessBuffer(data, 0, data.length, true);
        return raf;
    }

    @Override
    public InputStream getInputStreamUnbuffered() throws IOException {
        return getInputStream();
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        return getOutputStream();
    }

    private class ArrayBucketOutputStream extends ByteArrayOutputStream {
        public ArrayBucketOutputStream() {
            super();
        }

        @Override
        public synchronized void close() throws IOException {
            if (hasBeenClosed) {
                return;
            }
            data = super.toByteArray();
            if (readOnly) {
                throw new IOException("Read only");
            }
            // FIXME maybe we should throw on write instead? :)
            hasBeenClosed = true;
        }

        private boolean hasBeenClosed = false;
    }

    private final String name;
    private volatile byte[] data;
    private boolean readOnly;
    private boolean freed;
}
