package hyphanet.support.io.bucket;

import freenet.client.async.ClientContext;
import freenet.crypt.MasterSecret;
import freenet.support.api.Bucket;

import java.io.*;

public class NoFreeBucket implements Bucket, Serializable {

    static final int MAGIC = 0xa88da5c2;
    private static final long serialVersionUID = 1L;

    public NoFreeBucket(Bucket orig) {
        proxy = orig;
    }

    protected NoFreeBucket() {
        // For serialization.
        proxy = null;
    }

    protected NoFreeBucket(
        DataInputStream dis, FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker, MasterSecret masterKey)
        throws IOException, StorageFormatException, ResumeFailedException {
        proxy = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return proxy.getOutputStream();
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        return proxy.getOutputStreamUnbuffered();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return proxy.getInputStream();
    }

    @Override
    public InputStream getInputStreamUnbuffered() throws IOException {
        return proxy.getInputStreamUnbuffered();
    }

    @Override
    public String getName() {
        return proxy.getName();
    }

    @Override
    public long size() {
        return proxy.size();
    }

    @Override
    public boolean isReadOnly() {
        return proxy.isReadOnly();
    }

    @Override
    public void setReadOnly() {
        proxy.setReadOnly();
    }

    @Override
    public void free() {
        // Do nothing.
    }

    @Override
    public Bucket createShadow() {
        return proxy.createShadow();
    }

    @Override
    public void onResume(ClientContext context) throws ResumeFailedException {
        proxy.onResume(context);
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        proxy.storeTo(dos);
    }
    final Bucket proxy;

}
