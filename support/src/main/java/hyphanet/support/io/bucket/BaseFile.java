package hyphanet.support.io.bucket;

import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.StorageFormatException;
import hyphanet.support.io.randomaccessbuffer.PooledFile;
import hyphanet.support.io.randomaccessbuffer.RandomAccessBuffer;
import hyphanet.support.io.stream.NullInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseFile implements RandomAccess {
    public static final int MAGIC = 0xc4b7533d;
    static final int VERSION = 1;
    private static final Logger logger = LoggerFactory.getLogger(BaseFile.class);
    private static @Nullable Path tempDir = initializeTempDir();

    protected BaseFile() {
        assert (!(createFileOnly() && tempFileAlreadyExists())); // Mutually incompatible!
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
    public static synchronized String getTempDir() {
        if (tempDir == null) {
            return "";
        }
        return tempDir.toString();
    }

    /**
     * Set temp file directory.
     * <p>
     * The directory must exist.
     */
    public static synchronized void setTempDir(String dirName) {
        Path dir = Path.of(dirName);
        if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
            throw new IllegalArgumentException("Bad Temp Directory: " + dir.toAbsolutePath());
        }
        tempDir = dir;
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        synchronized (this) {
            var path = getPath();
            if (freed) {
                throw new IOException("File already freed: " + this);
            }
            if (isReadOnly()) {
                throw new IOException("Bucket is read-only: " + this);
            }

            if (
                // Fail if file already exists
                createFileOnly() &&
                // Ignore if we're just clobbering our own file after a previous
                // getOutputStream()
                fileRestartCounter == 0) {
                Files.createFile(path);
            }
            if (tempFileAlreadyExists() &&
                !(Files.exists(path) && Files.isReadable(path) && Files.isWritable(path))) {
                throw new FileNotFoundException(path.toString());
            }

            if (!streams.isEmpty()) {
                logger.error(
                    "Streams open on {} while opening an output stream!: {}",
                    this,
                    streams,
                    new Exception("debug")
                );
            }

            boolean rename = !tempFileAlreadyExists();
            var tempFile = rename ? getTempFilePath() : path;
            long streamNumber = ++fileRestartCounter;

            var os = new FileBucketOutputStream(tempFile, streamNumber);

            logger.debug("Creating Unbuffered FileBucketOutputStream {}", os);

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

        var path = getPath();
        if (!Files.exists(path)) {
            logger.info("File does not exist: {} for {}", path, this);
            return new NullInputStream();
        }

        var is = new FileBucketInputStream(path);
        addStream(is);
        logger.debug("Creating Unbuffered FileBucketInputStream {}", is);
        return is;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        var is = getInputStreamUnbuffered();
        if (is == null) {
            return null;
        }
        return new BufferedInputStream(is);
    }

    /**
     * @return the name of the file.
     */
    @Override
    public synchronized String getName() {
        return getPath().getFileName().toString();
    }

    @Override
    public synchronized long size() {
        try {
            return Files.size(getPath());
        } catch (Exception e) {
            return 0;
        }
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
        var path = getPath();
        return Arrays.stream(new int[bucketCount]).mapToObj(i -> {
            long startAt = (long) i * splitSize;
            long endAt = Math.min(startAt + splitSize, length);
            try {
                return new ReadOnlyFileSlice(path, startAt, endAt - startAt);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }).toArray(Bucket[]::new);

    }

    @Override
    public void close() {
        free(false);
    }

    public void free(boolean forceFree) {
        Set<Closeable> toClose;
        logger.info("Freeing {}", this);

        synchronized (this) {
            if (freed) {
                return;
            }
            freed = true;
            toClose = new HashSet<>(streams);
            streams.clear();
        }
        closeStreams(toClose);

        var path = getPath();
        if ((deleteOnFree() || forceFree) && Files.exists(path)) {
            logger.debug("Deleting bucket {}", path);
            deleteFile();
            if (Files.exists(path)) {
                logger.error("Delete failed on bucket");
            }
        }
    }

    @Override
    public synchronized String toString() {
        return String.format(
            "%s:%s:streams=%d",
            super.toString(),
            Optional.ofNullable(getPath()).map(Path::toString).orElse("???"),
            streams.isEmpty() ? 0 : streams.size()
        );
    }

    /**
     * Returns the path object this buckets data is kept in.
     */
    public abstract Path getPath();

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeBoolean(freed);
    }

    @Override
    public RandomAccessBuffer toRandomAccessBuffer() throws IOException {
        if (freed) {
            throw new IOException("Already freed");
        }
        setReadOnly();
        long size = size();
        if (size == 0) {
            throw new IOException("Must not be empty");
        }
        return new PooledFile(getPath(), true, size, getPersistentTempID(), deleteOnFree());
    }

    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        // Do nothing.
    }

    protected void setDeleteOnExit(File file) {
        file.deleteOnExit();
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

    protected abstract boolean deleteOnExit();

    protected abstract boolean deleteOnFree();

    /**
     * Create a temporary file in the same directory as this file.
     */
    protected Path getTempFilePath() throws IOException {
        var bucketPath = getPath();
        return FileIoUtil.createTempFile(
            bucketPath.getFileName().toString(),
            ".hyphanet-tmp",
            bucketPath.getParent()
        );
    }

    /**
     * Actually delete the underlying file. Called by finalizer, will not be called twice. But
     * length must still be valid when calling it.
     */
    protected synchronized void deleteFile() {
        var path = getPath();

        logger.info("Deleting {} for {}", path, this);

        try {
            Files.delete(path);
        } catch (Exception e) {
            logger.error("Delete failed on bucket {}", path, e);
        }
    }

    // determine the temp directory in one of several ways

    protected long getPersistentTempID() {
        return -1;
    }

    private void closeStreams(Set<Closeable> toClose) {
        if (toClose.isEmpty()) {
            return;
        }

        logger.info("Streams open free()ing {} : {}", this, toClose);
        for (Closeable stream : toClose) {
            try {
                stream.close();
            } catch (Exception e) {
                logger.error("Caught closing stream in free()", e);
            }
        }
    }

    private static Path initializeTempDir() {
        // Try the Java property first
        String dir = System.getProperty("java.io.tmpdir");
        if (dir != null) {
            return Path.of(dir);
        }

        // Try OS-specific locations
        Path osSpecificTemp = findOsSpecificTempDir();
        if (osSpecificTemp != null) {
            return osSpecificTemp;
        }

        // Last resort - use current working directory
        return Path.of(System.getProperty("user.dir"));
    }

    private static @Nullable Path findOsSpecificTempDir() {
        String os = System.getProperty("os.name");
        if (os == null) {
            return null;
        }

        String[] candidates = null;
        if (os.equalsIgnoreCase("Linux") || os.equalsIgnoreCase("FreeBSD")) {
            candidates = new String[]{"/tmp", "/var/tmp"};
        } else if (os.equalsIgnoreCase("Windows")) {
            candidates = new String[]{"C:\\TEMP", "C:\\WINDOWS\\TEMP"};
        }
        if (candidates == null) {
            return null;
        }

        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path) && Files.isWritable(path)) {
                return path;
            }
        }
        return null;
    }

    private synchronized void addStream(Closeable stream) {
        // BaseFileBucket is a very common object, and often very long-lived,
        // so we need to minimize memory usage even at the cost of frequent allocations.
        streams.add(stream);
    }

    private synchronized void removeStream(Closeable stream) {
        // Race condition is possible
        if (streams.isEmpty()) {
            return;
        }
        streams.remove(stream);
    }

    /**
     * Internal OutputStream impl. If createFileOnly is set, we won't overwrite an existing
     * file, and we write to a temp file then rename over the target. Note that we can't use
     * createNewFile then new FOS() because while createNewFile is atomic, the combination is
     * not, so if we do it we are vulnerable to symlink attacks.
     *
     * @author toad
     */
    class FileBucketOutputStream extends OutputStream {

        protected FileBucketOutputStream(Path tempFilePath, long restartCount)
            throws IOException {
            super();
            if (deleteOnExit()) {
                outputStream = Files.newOutputStream(
                    tempFilePath,
                    StandardOpenOption.DELETE_ON_CLOSE
                );
            } else {
                outputStream = Files.newOutputStream(tempFilePath);
            }
            logger.atInfo()
                  .setMessage("Writing to {} for {} : {}")
                  .addArgument(tempFilePath)
                  .addArgument(BaseFile.this::getPath)
                  .addArgument(this)
                  .log();
            this.tempFilePath = tempFilePath;
            this.restartCount = restartCount;
            closed = false;
        }

        @Override
        public void write(byte[] b) throws IOException {
            synchronized (BaseFile.this) {
                confirmWriteSynchronized();
                outputStream.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            synchronized (BaseFile.this) {
                confirmWriteSynchronized();
                outputStream.write(b, off, len);
            }
        }

        @Override
        public void write(int b) throws IOException {
            synchronized (BaseFile.this) {
                confirmWriteSynchronized();
                outputStream.write(b);
            }
        }

        @Override
        public void close() throws IOException {
            Path path;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                path = getPath();
            }
            boolean renaming = !tempFileAlreadyExists();
            removeStream(this);

            logger.info("Closing {}", BaseFile.this);

            try {
                outputStream.close();
            } finally {
                if (renaming) {
                    Files.delete(tempFilePath);
                }
            }

            if (renaming && !FileIoUtil.renameTo(tempFilePath, path)) {
                // getOutputStream() creates the file as a marker, so DON'T check for its
                // existence,
                // even if createFileOnly() is true.
                Files.delete(tempFilePath);
                logger.info("Deleted, cannot rename file for {}", this);
                throw new IOException("Cannot rename file");
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
        private final Path tempFilePath;
        private final OutputStream outputStream;
        private boolean closed;
    }

    class FileBucketInputStream extends InputStream {

        public FileBucketInputStream(Path path) throws IOException {
            super();
            inputStream = Files.newInputStream(path);
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inputStream.read(b, off, len);
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

        private final InputStream inputStream;
        boolean closed;

    }

    /**
     * Vector of streams (FileBucketInputStream or FileBucketOutputStream) which are open to
     * this file. So we can be sure they are all closed when we free it. Can be null.
     */
    private final Set<Closeable> streams = ConcurrentHashMap.newKeySet();
    protected volatile long fileRestartCounter;

    /**
     * Has the bucket been freed? If so, no further operations may be done
     */
    private volatile boolean freed;
}
