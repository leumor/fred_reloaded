package hyphanet.support.io.randomaccessbuffer;

import freenet.client.async.ClientContext;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

public class ByteArrayRandomAccessBuffer implements RandomAccessBuffer, Serializable {

    private static final long serialVersionUID = 1L;

    public ByteArrayRandomAccessBuffer(byte[] padded) {
        this.data = padded;
    }

    public ByteArrayRandomAccessBuffer(int size) {
        this.data = new byte[size];
    }

    public ByteArrayRandomAccessBuffer(
        byte[] initialContents, int offset, int size, boolean readOnly) {
        data = Arrays.copyOfRange(initialContents, offset, offset + size);
        this.readOnly = readOnly;
    }

    protected ByteArrayRandomAccessBuffer() {
        // For serialization.
        data = null;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public synchronized void pread(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        if (closed) {
            throw new IOException("Closed");
        }
        if (fileOffset < 0) {
            throw new IllegalArgumentException("Cannot read before zero");
        }
        if (fileOffset + length > data.length) {
            throw new IOException(
                "Cannot read after end: trying to read from " + fileOffset + " to " +
                (fileOffset + length) + " on block length " + data.length);
        }
        System.arraycopy(data, (int) fileOffset, buf, bufOffset, length);
    }

    @Override
    public synchronized void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        if (closed) {
            throw new IOException("Closed");
        }
        if (fileOffset < 0) {
            throw new IllegalArgumentException("Cannot write before zero");
        }
        if (fileOffset + length > data.length) {
            throw new IOException(
                "Cannot write after end: trying to write from " + fileOffset + " to " +
                (fileOffset + length) + " on block length " + data.length);
        }
        if (readOnly) {
            throw new IOException("Read-only");
        }
        System.arraycopy(buf, bufOffset, data, (int) fileOffset, length);
    }

    @Override
    public long size() {
        return data.length;
    }

    public synchronized void setReadOnly() {
        readOnly = true;
    }

    public synchronized boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public RabLock lockOpen() {
        return new RabLock() {

            @Override
            protected void innerUnlock() {
                // Do nothing. Always open.
            }

        };
    }

    @Override
    public void dispose() {
        // Do nothing.
    }

    @Override
    public void onResume(ClientContext context) {
        // Do nothing.
    }

    @Override
    public void storeTo(DataOutputStream dos) {
        throw new UnsupportedOperationException();
    }

    /**
     * Package-local!
     */
    byte[] getBuffer() {
        return data;
    }

    private final byte[] data;
    private boolean readOnly;
    private boolean closed;

    // Default hashCode() and equals() are correct for this type.

}
