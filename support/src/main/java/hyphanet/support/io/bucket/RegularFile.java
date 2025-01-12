/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.bucket;

import hyphanet.support.io.StorageFormatException;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A file Bucket is an implementation of Bucket that writes to a file.
 *
 * @author oskar
 */
public class RegularFile extends BaseFile implements Bucket, Serializable {

    public static final int MAGIC = 0x8fe6e41b;
    static final int VERSION = 1;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new FileBucket.
     *
     * @param path           The File to read and write to.
     * @param readOnly       If true, any attempt to write to the bucket will result in an
     *                       IOException. Can be set later. Irreversible. @see isReadOnly(),
     *                       setReadOnly()
     * @param createFileOnly If true, create the file if it doesn't exist, but if it does
     *                       exist, throw a FileExistsException on any write operation. This is
     *                       safe against symlink attacks because we write to a temp file and
     *                       then rename. It is technically possible that the rename will
     *                       clobber an existing file if there is a race condition, but since
     *                       it will not write over a symlink this is probably not dangerous.
     *                       User-supplied filenames should in any case be restricted by higher
     *                       levels.
     * @param deleteOnExit   If true, delete the file on a clean exit of the JVM. Irreversible
     *                       - use with care!
     * @param deleteOnFree   If true, delete the file on finalization. Reversible.
     */
    public RegularFile(
        Path path,
        boolean readOnly,
        boolean createFileOnly,
        boolean deleteOnExit,
        boolean deleteOnFree
    ) {
        super();

        this.readOnly = readOnly;
        this.createFileOnly = createFileOnly;
        this.path = path;
        this.deleteOnFree = deleteOnFree;
        this.deleteOnExit = deleteOnExit;
        // Useful for finding temp file leaks.
        // System.err.println("-- FileBucket.ctr(0) -- " +
        // file.getAbsolutePath());
        // (new Exception("get stack")).printStackTrace();
        fileRestartCounter = 0;
    }
    // JVM caches File.size() and there is no way to flush the cache, so we
    // need to track it ourselves

    @SuppressWarnings("unused")
    protected RegularFile() {
        // For serialization.
        super();
        path = null;
        deleteOnExit = false;
        createFileOnly = false;
    }

    protected RegularFile(DataInputStream dis) throws IOException, StorageFormatException {
        super(dis);
        int version = dis.readInt();
        if (version != VERSION) {
            throw new StorageFormatException("Bad version");
        }
        path = Path.of(dis.readUTF());
        readOnly = dis.readBoolean();
        deleteOnFree = dis.readBoolean();
        deleteOnExit = false;
        createFileOnly = dis.readBoolean();
    }

    /**
     * Returns the file object this buckets data is kept in.
     */
    @Override
    public synchronized Path getPath() {
        return path;
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
    public RandomAccessable createShadow() {
        return new RegularFile(getPath(), true, false, false, false);
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        super.storeTo(dos);
        dos.writeInt(VERSION);
        assert path != null;
        dos.writeUTF(path.toString());
        dos.writeBoolean(readOnly);
        dos.writeBoolean(deleteOnFree);
        if (deleteOnExit) {
            throw new IllegalStateException("Must not free on exit if persistent");
        }
        dos.writeBoolean(createFileOnly);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (createFileOnly ? 1231 : 1237);
        result = prime * result + (deleteOnExit ? 1231 : 1237);
        result = prime * result + (deleteOnFree ? 1231 : 1237);
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + (readOnly ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RegularFile other = (RegularFile) obj;
        if (createFileOnly != other.createFileOnly) {
            return false;
        }
        if (deleteOnExit != other.deleteOnExit) {
            return false;
        }
        if (deleteOnFree != other.deleteOnFree) {
            return false;
        }
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        return readOnly == other.readOnly;
    }

    @Override
    protected boolean createFileOnly() {
        return createFileOnly;
    }

    @Override
    protected boolean deleteOnExit() {
        return deleteOnExit;
    }

    @Override
    protected boolean deleteOnFree() {
        return deleteOnFree;
    }

    @Override
    protected boolean tempFileAlreadyExists() {
        return false;
    }


    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        assert path != null;
        out.writeUTF(path.toString());
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        path = Paths.get(in.readUTF());
    }

    protected final boolean deleteOnExit;
    protected final boolean createFileOnly;
    protected transient @Nullable Path path;
    protected boolean readOnly;
    protected boolean deleteOnFree;


}
