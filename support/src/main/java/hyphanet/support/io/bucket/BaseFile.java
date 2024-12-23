package hyphanet.support.io.bucket;

import hyphanet.support.io.FileDoesNotExistException;
import hyphanet.support.io.FileExistsException;
import hyphanet.support.io.FileUtil;
import hyphanet.support.io.StorageFormatException;
import hyphanet.support.io.randomaccessbuffer.Lockable;
import hyphanet.support.io.randomaccessbuffer.PooledFile;
import hyphanet.support.io.stream.NullInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Vector;

public abstract class BaseFile implements RandomAccess {
    public static final int MAGIC = 0xc4b7533d;
    static final int VERSION = 1;
    private static final Logger logger = LoggerFactory.getLogger(BaseFile.class);
    protected static String tempDir = null;

    static {
        // Try the Java property (1.2 and above)
        tempDir = System.getProperty("java.io.tmpdir");

        // Deprecated calls removed.

        // Try TEMP and TMP
        //	if (tempDir == null) {
        //	    tempDir = System.getenv("TEMP");
        //	}

        //	if (tempDir == null) {
        //	    tempDir = System.getenv("TMP");
        //	}

        // make some semi-educated guesses based on OS.

        if (tempDir == null) {
            String os = System.getProperty("os.name");
            if (os != null) {

                String[] candidates = null;

                // XXX: Add more possible OSes here.
                if (os.equalsIgnoreCase("Linux") || os.equalsIgnoreCase("FreeBSD")) {
                    String[] linuxCandidates = {"/tmp", "/var/tmp"};
                    candidates = linuxCandidates;
                } else if (os.equalsIgnoreCase("Windows")) {
                    String[] windowsCandidates = {"C:\\TEMP", "C:\\WINDOWS\\TEMP"};
                    candidates = windowsCandidates;
                }

                if (candidates != null) {
                    for (String candidate : candidates) {
                        File path = new File(candidate);
                        if (path.exists() && path.isDirectory() && path.canWrite()) {
                            tempDir = candidate;
                            break;
                        }
                    }
                }
            }
        }

        // last resort -- use current working directory

        if (tempDir == null) {
            // This can be null -- but that's OK, null => cwd for File
            // constructor, anyways.
            tempDir = System.getProperty("user.dir");
        }
    }

    /**
     * Constructor.
     *
     * @param file
     * @param deleteOnExit If true, call File.deleteOnExit() on the file. WARNING: Delete on
     *                     exit is a memory leak: The filenames are kept until the JVM exits,
     *                     and cannot be removed even when the file has been deleted! It should
     *                     only be used where it is ESSENTIAL! Note that if you want temp files
     *                     to be deleted on exit, you also need to override deleteOnExit().
     */
    public BaseFile(File file, boolean deleteOnExit) {
        if (file == null) {
            throw new NullPointerException();
        }
        maybeSetDeleteOnExit(deleteOnExit, file);
        assert (!(createFileOnly() && tempFileAlreadyExists())); // Mutually incompatible!
    }

    protected BaseFile() {
        // For serialization.
    }

    protected BaseFile(DataInputStream dis) throws IOException, StorageFormatException {
        // Not constructed directly, so we DO need to read the magic value.
        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new StorageFormatException("Bad magic");
        }
        int version = dis.readInt();
        if (version != VERSION) {
            throw new StorageFormatException("Bad version");
        }
        freed = dis.readBoolean();
    }

    /**
     * Return directory used for temp files.
     */
    public synchronized static String getTempDir() {
        return tempDir;  // **FIXME**/TODO: locking on tempDir needs to be checked by a Java
        // guru for consistency
    }

    /**
     * Set temp file directory.
     * <p>
     * The directory must exist.
     */
    public synchronized static void setTempDir(String dirName) {
        File dir = new File(dirName);
        if (!(dir.exists() && dir.isDirectory() && dir.canWrite())) {
            throw new IllegalArgumentException("Bad Temp Directory: " + dir.getAbsolutePath());
        }
        tempDir = dirName;  // **FIXME**/TODO: locking on tempDir needs to be checked by a Java
        // guru for consistency
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        synchronized (this) {
            File file = getFile();
            if (freed) {
                throw new IOException("File already freed: " + this);
            }
            if (isReadOnly()) {
                throw new IOException("Bucket is read-only: " + this);
            }

            if (createFileOnly() && // Fail if file already exists
                fileRestartCounter == 0 &&
                // Ignore if we're just clobbering our own file after a previous
                // getOutputStream()
                !file.createNewFile()) {
                throw new FileExistsException(file);
            }
            if (tempFileAlreadyExists() &&
                !(file.exists() && file.canRead() && file.canWrite())) {
                throw new FileDoesNotExistException(file);
            }

            if (streams != null && !streams.isEmpty()) {
                logger.error("Streams open on {} while opening an output stream!: {}", this,
                             streams, new Exception("debug"));
            }

            boolean rename = !tempFileAlreadyExists();
            File tempfile = rename ? getTempfile() : file;
            long streamNumber = ++fileRestartCounter;

            FileBucketOutputStream os = new FileBucketOutputStream(tempfile, streamNumber);

            logger.debug("Creating {}", os, new Exception("debug"));

            addStream(os);
            return os;
        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new BufferedOutputStream(getOutputStreamUnbuffered());
    }

    @Override
    public synchronized InputStream getInputStreamUnbuffered() throws IOException {
        if (freed) {
            throw new IOException("File already freed: " + this);
        }
        File file = getFile();
        if (!file.exists()) {
            logger.info("File does not exist: {} for {}", file, this);
            return new NullInputStream();
        } else {
            FileBucketInputStream is = new FileBucketInputStream(file);
            addStream(is);
            logger.debug("Creating {}", is, new Exception("debug"));
            return is;
        }
    }

    public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(getInputStreamUnbuffered());
    }

    /**
     * @return the name of the file.
     */
    @Override
    public synchronized String getName() {
        return getFile().getName();
    }

    @Override
    public synchronized long size() {
        return getFile().length();
    }

    public synchronized Bucket[] split(int splitSize) {
        long length = size();
        if (length > ((long) Integer.MAX_VALUE) * splitSize) {
            throw new IllegalArgumentException(
                "Way too big!: " + length + " for " + splitSize);
        }
        int bucketCount = (int) (length / splitSize);
        if (length % splitSize > 0) {
            bucketCount++;
        }
        Bucket[] buckets = new Bucket[bucketCount];
        File file = getFile();
        for (int i = 0; i < buckets.length; i++) {
            long startAt = (long) i * splitSize;
            long endAt = Math.min(startAt + (long) splitSize, length);
            long len = endAt - startAt;
            buckets[i] = new ReadOnlyFileSliceBucket(file, startAt, len);
        }
        return buckets;
    }

    @Override
    public void free() {
        free(false);
    }

    public void free(boolean forceFree) {
        Closeable[] toClose;
        logger.info("Freeing {}", this, new Exception("debug"));

        synchronized (this) {
            if (freed) {
                return;
            }
            freed = true;
            toClose = streams == null ? null : streams.toArray(new Closeable[streams.size()]);
            streams = null;
        }

        if (toClose != null) {
            logger.error("Streams open free()ing {} : {}", this, Arrays.toString(toClose),
                         new Exception("debug"));
            for (Closeable strm : toClose) {
                try {
                    strm.close();
                } catch (IOException e) {
                    logger.error("Caught closing stream in free(): {}", e, e);
                } catch (Throwable t) {
                    logger.error("Caught closing stream in free(): {}", t, t);
                }
            }
        }

        File file = getFile();
        if ((deleteOnFree() || forceFree) && file.exists()) {
            logger.debug("Deleting bucket {}", file, new Exception("debug"));
            deleteFile();
            if (file.exists()) {
                logger.error("Delete failed on bucket {}", file, new Exception("debug"));
            }
        }
    }

    @Override
    public synchronized String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(super.toString());
        sb.append(':');
        File f = getFile();
        if (f != null) {
            sb.append(f.getPath());
        } else {
            sb.append("???");
        }
        sb.append(":streams=");
        sb.append(streams == null ? 0 : streams.size());
        return sb.toString();
    }

    /**
     * Returns the file object this buckets data is kept in.
     */
    public abstract File getFile();

    // TODO
    //    @Override
    //    public void onResume(ClientContext context) throws ResumeFailedException {
    //        // Do nothing.
    //    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeBoolean(freed);
    }

    @Override
    public Lockable toRandomAccessBuffer() throws IOException {
        if (freed) {
            throw new IOException("Already freed");
        }
        setReadOnly();
        long size = size();
        if (size == 0) {
            throw new IOException("Must not be empty");
        }
        return new PooledFile(getFile(), true, size, null, getPersistentTempID(),
                              deleteOnFree());
    }

    protected void setDeleteOnExit(File file) {
        try {
            file.deleteOnExit();
        } catch (NullPointerException e) {
            // TODO
            //            if (WrapperManager.hasShutdownHookBeenTriggered()) {
            //                Logger.normal(this,
            //                              "NullPointerException setting deleteOnExit while
            //                              shutting down" +
            //                              " - buggy JVM code: " + e, e);
            //            } else {
            //                Logger.error(this, "Caught " + e + " doing deleteOnExit() for
            //                " + file +
            //                                   " - JVM bug ????");
            //            }
        }
    }

    /**
     * If true, then the file is temporary and must already exist, so we will just open it.
     * Otherwise we will create a temporary file and then rename it over the target.
     * Incompatible with createFileOnly()!
     */
    protected abstract boolean tempFileAlreadyExists();

    /**
     * If true, we will fail if the file already exist. Incompatible with
     * tempFileAlreadyExists()!
     */
    protected abstract boolean createFileOnly();

    // determine the temp directory in one of several ways

    protected abstract boolean deleteOnExit();

    protected abstract boolean deleteOnFree();

    /**
     * Create a temporary file in the same directory as this file.
     */
    protected File getTempfile() throws IOException {
        File file = getFile();
        File f =
            FileUtil.createTempFile(file.getName(), ".hyphanet-tmp", file.getParentFile());
        if (deleteOnExit()) {
            f.deleteOnExit();
        }
        return f;
    }

    /**
     * Actually delete the underlying file. Called by finalizer, will not be called twice. But
     * length must still be valid when calling it.
     */
    protected synchronized void deleteFile() {
        logger.info("Deleting {} for {}", getFile(), this, new Exception("debug"));

        getFile().delete();
    }

    protected long getPersistentTempID() {
        return -1;
    }

    private void maybeSetDeleteOnExit(boolean deleteOnExit, File file) {
        if (deleteOnExit) {
            setDeleteOnExit(file);
        }
    }

    private synchronized void addStream(Closeable stream) {
        // BaseFileBucket is a very common object, and often very long lived,
        // so we need to minimize memory usage even at the cost of frequent allocations.
        if (streams == null) {
            streams = new Vector<Closeable>(1, 1);
        }
        streams.add(stream);
    }

    private synchronized void removeStream(Closeable stream) {
        // Race condition is possible
        if (streams == null) {
            return;
        }
        streams.remove(stream);
        if (streams.isEmpty()) {
            streams = null;
        }
    }

    /**
     * Internal OutputStream impl. If createFileOnly is set, we won't overwrite an existing
     * file, and we write to a temp file then rename over the target. Note that we can't use
     * createNewFile then new FOS() because while createNewFile is atomic, the combination is
     * not, so if we do it we are vulnerable to symlink attacks.
     *
     * @author toad
     */
    class FileBucketOutputStream extends FileOutputStream {

        protected FileBucketOutputStream(File tempfile, long restartCount)
            throws FileNotFoundException {
            super(tempfile, false);
            logger.atInfo().setMessage("Writing to {} for {} : {}").addArgument(tempfile)
                  .addArgument(BaseFile.this::getFile).addArgument(this).log();
            this.tempfile = tempfile;
            this.restartCount = restartCount;
            closed = false;
        }

        @Override
        public void write(byte[] b) throws IOException {
            synchronized (BaseFile.this) {
                confirmWriteSynchronized();
                super.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            synchronized (BaseFile.this) {
                confirmWriteSynchronized();
                super.write(b, off, len);
            }
        }

        @Override
        public void write(int b) throws IOException {
            synchronized (BaseFile.this) {
                confirmWriteSynchronized();
                super.write(b);
            }
        }

        @Override
        public void close() throws IOException {
            File file;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                file = getFile();
            }
            boolean renaming = !tempFileAlreadyExists();
            removeStream(this);

            logger.info("Closing {}", BaseFile.this);

            try {
                super.close();
            } catch (IOException e) {
                logger.info("Failed closing {} : {}", BaseFile.this, e, e);
                if (renaming) {
                    tempfile.delete();
                }
                throw e;
            }
            if (renaming) {
                // getOutputStream() creates the file as a marker, so DON'T check for its
                // existence,
                // even if createFileOnly() is true.
                if (!FileUtil.renameTo(tempfile, file)) {
                    tempfile.delete();
                    logger.info("Deleted, cannot rename file for {}", this);
                    throw new IOException("Cannot rename file");
                }
            }
        }

        @Override
        public String toString() {
            return super.toString() + ":" + BaseFile.this;
        }

        protected void confirmWriteSynchronized() throws IOException {
            synchronized (BaseFile.this) {
                if (fileRestartCounter > restartCount) {
                    throw new IllegalStateException("writing to file after restart");
                }
                if (freed) {
                    throw new IOException("writing to file after it has been freed");
                }
            }
            if (isReadOnly()) {
                throw new IOException("File is read-only");
            }

        }

        private final long restartCount;
        private final File tempfile;
        private boolean closed;
    }

    class FileBucketInputStream extends FileInputStream {
        public FileBucketInputStream(File f) throws IOException {
            super(f);
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
            }
            removeStream(this);
            super.close();
        }

        @Override
        public String toString() {
            return super.toString() + ":" + BaseFile.this;
        }

        boolean closed;
    }

    protected long fileRestartCounter;
    /**
     * Has the bucket been freed? If so, no further operations may be done
     */
    private boolean freed;
    /**
     * Vector of streams (FileBucketInputStream or FileBucketOutputStream) which are open to
     * this file. So we can be sure they are all closed when we free it. Can be null.
     */
    private transient Vector<Closeable> streams;

}
