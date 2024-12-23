package hyphanet.support.io.stream;

import freenet.support.api.RandomAccessBuffer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class RAFInputStream extends InputStream {

    public RAFInputStream(RandomAccessBuffer data, long offset, long size) {
        this.underlying = data;
        this.rafOffset = offset;
        this.rafLength = size;
    }

    @Override
    public int read() throws IOException {
        read(oneByte);
        return oneByte[0];
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
        if (rafOffset >= rafLength) {
            throw new EOFException();
        }
        length = (int) Math.min(length, rafLength - rafOffset);
        underlying.pread(rafOffset, buf, offset, length);
        rafOffset += length;
        return length;
    }
    private final RandomAccessBuffer underlying;
    private final byte[] oneByte = new byte[1];
    private long rafOffset;
    private final long rafLength;

}
