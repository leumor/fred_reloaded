package hyphanet.support.io.bucket;

import freenet.client.async.ClientContext;
import freenet.crypt.MasterSecret;
import freenet.support.api.Bucket;
import freenet.support.api.LockableRandomAccessBuffer;
import freenet.support.api.RandomAccessBucket;

import java.io.*;

public class RAFBucket implements Bucket, RandomAccessBucket {

    static final int MAGIC = 0x892a708a;

    public RAFBucket(LockableRandomAccessBuffer underlying) throws IOException {
        this.underlying = underlying;
        size = underlying.size();
    }

    RAFBucket(
        DataInputStream dis, FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker, MasterSecret masterKey)
        throws IOException, StorageFormatException, ResumeFailedException {
        underlying = BucketTools.restoreRabFrom(dis, fg, persistentFileTracker, masterKey);
        size = underlying.size();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(getInputStreamUnbuffered());
    }

    @Override
    public InputStream getInputStreamUnbuffered() throws IOException {
        return new RAFInputStream(underlying, 0, underlying.size());
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void setReadOnly() {
        // Ignore.
    }

    @Override
    public void free() {
        underlying.free();
    }

    @Override
    public RandomAccessBucket createShadow() {
        return null;
    }

    @Override
    public void onResume(ClientContext context) throws ResumeFailedException {
        underlying.onResume(context);
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        underlying.storeTo(dos);
    }

    @Override
    public LockableRandomAccessBuffer toRandomAccessBuffer() {
        return underlying;
    }

    final long size;
    private final LockableRandomAccessBuffer underlying;

}
