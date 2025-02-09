package hyphanet.support.io.storage.bucket;

import freenet.client.async.ClientContext;
import freenet.support.Logger;
import freenet.support.api.RandomAccessBucket;

import java.io.*;

public class PersistentTempFileBucket extends TempFileBucket implements Serializable {

    public static final int MAGIC = 0x2ffdd4cf;
    static final int BUFFER_SIZE = 4096;
    private static final long serialVersionUID = 1L;
    private static volatile boolean logMINOR;

    static {
        Logger.registerClass(PersistentTempFileBucket.class);
    }

    public PersistentTempFileBucket(
        long id, FilenameGenerator generator, PersistentFileTracker tracker) {
        this(id, generator, tracker, true);
    }

    protected PersistentTempFileBucket(
        long id, FilenameGenerator generator, PersistentFileTracker tracker,
        boolean deleteOnFree) {
        super(id, generator, deleteOnFree);
        this.tracker = tracker;
    }

    protected PersistentTempFileBucket() {
        // For serialization.
    }

    protected PersistentTempFileBucket(DataInputStream dis)
        throws IOException, StorageFormatException {
        super(dis);
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        OutputStream os = super.getOutputStreamUnbuffered();
        os = new DiskSpaceCheckingOutputStream(os, tracker, getPath(), BUFFER_SIZE);
        return os;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new BufferedOutputStream(getOutputStreamUnbuffered(), BUFFER_SIZE);
    }

    /**
     * Must override createShadow() so it creates a persistent bucket, which will have
     * deleteOnExit() = deleteOnFinalize() = false.
     */
    @Override
    public RandomAccessBucket createShadow() {
        PersistentTempFileBucket ret =
            new PersistentTempFileBucket(filenameId, generator, tracker, false);
        ret.setReadOnly();
		if (!getPath().exists()) {
			Logger.error(this, "File does not exist when creating shadow: " + getPath());
		}
        return ret;
    }

    @Override
    protected boolean deleteOnExit() {
        // DO NOT DELETE ON EXIT !!!!
        return false;
    }

    @Override
    protected void innerResume(ClientContext context) throws ResumeFailedException {
        super.innerResume(context);
		if (logMINOR) {
			Logger.minor(this, "Resuming " + this, new Exception("debug"));
		}
        tracker = context.persistentFileTracker;
        tracker.register(getPath());
    }

    @Override
    protected boolean persistent() {
        return true;
    }

    protected int magic() {
        return MAGIC;
    }

    @Override
    protected long getPersistentTempID() {
        return filenameId;
    }
    transient PersistentFileTracker tracker;

}
