package hyphanet.support.io.bucket;

import freenet.client.async.ClientContext;
import freenet.node.NodeStarter;
import freenet.support.api.Bucket;
import freenet.support.io.*;

import java.io.*;
import java.util.Arrays;

/**
 * Encrypted and authenticated Bucket implementation using AES cipher and OCB mode. Warning:
 * Avoid using Closer.close() on InputStream's opened on this Bucket. The MAC is only checked
 * when the end of the bucket is reached, which may be in read() or may be in close().
 *
 * @author toad
 */
public class AEADCryptBucket implements Bucket, Serializable {

    public static final int MAGIC = 0xb25b32d6;
    static final int OVERHEAD = AEADOutputStream.AES_OVERHEAD;
    static final int VERSION = 1;
    private static final long serialVersionUID = 1L;
    public AEADCryptBucket(Bucket underlying, byte[] key) {
        this.underlying = underlying;
        this.key = Arrays.copyOf(key, key.length);
    }

    public AEADCryptBucket(
        DataInputStream dis, FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker, MasterSecret masterKey)
        throws IOException, StorageFormatException, ResumeFailedException {
        // Magic already read by caller.
        int version = dis.readInt();
        if (version != VERSION) {
            throw new StorageFormatException("Unknown version " + version);
        }
        int keyLength = dis.readByte();
        if (keyLength < 0 || !(keyLength == 16 || keyLength == 24 || keyLength == 32)) {
            throw new StorageFormatException("Unknown key length " +
                                             keyLength); // FIXME validate this in a more permanent way
        }
        key = new byte[keyLength];
        dis.readFully(key);
        readOnly = dis.readBoolean();
        underlying = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
    }

    protected AEADCryptBucket() {
        // For serialization.
        underlying = null;
        key = null;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new BufferedOutputStream(getOutputStreamUnbuffered());
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        synchronized (this) {
            if (readOnly) {
                throw new IOException("Read only");
            }
        }
        OutputStream os = underlying.getOutputStreamUnbuffered();
        return AEADOutputStream.createAES(os, key, NodeStarter.getGlobalSecureRandom());
    }

    public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(getInputStreamUnbuffered());
    }

    @Override
    public InputStream getInputStreamUnbuffered() throws IOException {
        InputStream is = underlying.getInputStreamUnbuffered();
        return AEADInputStream.createAES(is, key);
    }

    @Override
    public String getName() {
        return "AEADEncrypted:" + underlying.getName();
    }

    @Override
    public long size() {
        return underlying.size() - OVERHEAD;
    }

    @Override
    public synchronized boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public synchronized void setReadOnly() {
        readOnly = true;
    }

    @Override
    public void free() {
        underlying.free();
    }

    @Override
    public Bucket createShadow() {
        Bucket undershadow = underlying.createShadow();
        AEADCryptBucket ret = new AEADCryptBucket(undershadow, key);
        ret.setReadOnly();
        return ret;
    }

    @Override
    public void onResume(ClientContext context) throws ResumeFailedException {
        underlying.onResume(context);
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeByte(key.length);
        dos.write(key);
        dos.writeBoolean(readOnly);
        underlying.storeTo(dos);
    }
    private final Bucket underlying;
    private final byte[] key;
    private boolean readOnly;

}
