/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import java.nio.file.Path;

/**
 * Interface for tracking and managing persistent files in a file system. Implements
 * functionality for garbage collection, file registration, and delayed disposal of temporary
 * files.
 * <p>
 * This tracker extends {@link DiskSpaceChecker} to ensure sufficient disk space is available
 * for operations.
 * </p>
 */
public interface PersistentFileTracker extends DiskSpaceChecker {

    /**
     * Registers a file with the garbage collector during system resume.
     * <p>
     * This method prevents registered files from being deleted during startup cleanup of the
     * persistent-temp directory. Only unregistered files that were present at startup will be
     * removed during cleanup.
     * </p>
     *
     * @param path the {@link Path} to the file that needs to be registered
     */
    void register(Path path);

    /**
     * Returns the current transaction commit ID.
     * <p>
     * This ID is incremented with each transaction and serves as a unique identifier for
     * tracking file operations.
     * </p>
     *
     * @return a positive long number representing the current commit ID
     */
    long commitID();

    /**
     * Schedules a bucket for disposal after the next disk serialization.
     *
     * @param bucket          the {@link DelayedDispose} bucket to be disposed
     * @param createdCommitID the commit ID when the bucket was created. Use 0 for buckets
     *                        created before the last node restart. If no commit has occurred
     *                        since creation, the bucket can be disposed immediately
     */
    void delayedDispose(DelayedDispose bucket, long createdCommitID);

    /**
     * Retrieves the directory path for persistent temporary files.
     *
     * @return a {@link Path} object representing the persistent temp files directory
     */
    Path getDir();

    /**
     * Retrieves the filename generator for creating unique filenames.
     *
     * @return a {@link FilenameGenerator} instance used for generating unique filenames
     */
    FilenameGenerator getGenerator();

}
