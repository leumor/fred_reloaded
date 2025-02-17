/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.DiskSpaceChecker;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.storage.DelayedDisposable;
import hyphanet.support.io.storage.bucket.wrapper.DelayedDisposeRandomAccessBucket;
import hyphanet.support.io.storage.bucket.wrapper.EncryptedBucket;
import hyphanet.support.io.storage.bucket.wrapper.PaddedRandomAccessBucket;
import hyphanet.support.io.util.FilePath;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static hyphanet.support.io.storage.Storage.CRYPT_TYPE;

/**
 * Handles persistent temporary files. These are used for e.g. persistent downloads. These are
 * temporary files in the directory specified for the {@link PersistentFileTracker} (which supports
 * changing the directory, i.e. moving the files).
 *
 * <p>These temporary files are encrypted using an ephemeral key (unless the node is configured not
 * to encrypt temporary files as happens with physical security level LOW).
 *
 * <p><b>Persistence and Transactional Deletion:</b> This class is crucial for persistence and
 * transactional file management. It ensures that temporary files are only deleted <em>after</em>
 * the transaction containing their deletion is successfully written to disk. This mechanism
 * prevents data loss or corruption in case of unclean shutdowns or system crashes. It is essential
 * to avoid leaking temporary files or attempting to reuse deleted buckets if a system restart
 * occurs before a transaction is fully persisted.
 *
 * <p>Although this class is involved in persistence, it is not itself Serializable. Instead, it is
 * recreated on every startup. {@link PersistentTempFileBucket PersistentTempFileBuckets} register
 * themselves with this factory upon creation, allowing for management across restarts.
 *
 * <p><b>Encryption:</b> Temporary files are encrypted to protect sensitive data at rest. The
 * encryption is applied using an ephemeral key, enhancing security. However, encryption can be
 * disabled based on system configuration, such as in low physical security environments.
 *
 * @see PersistentFileTracker
 * @see BucketFactory
 * @see PersistentTempFileBucket
 * @see DelayedDisposeRandomAccessBucket
 * @see EncryptedBucket
 * @see PaddedRandomAccessBucket
 */
public class PersistentTempFileBucketFactory implements BucketFactory, PersistentFileTracker {

  private static final Logger logger =
      LoggerFactory.getLogger(PersistentTempFileBucketFactory.class);

  /**
   * Create a persistent temporary bucket factory.
   *
   * @param dir Where to put temporary files. This directory will be used by the {@link
   *     FilenameGenerator}.
   * @param prefix Prefix for temporary file names. Used by the {@link FilenameGenerator} to create
   *     unique names.
   * @param weakPRNG Weak but fast random number generator. Used for general purpose randomness in
   *     filename generation.
   * @param encrypt Whether to encrypt temporary files. If true, buckets created by this factory
   *     will be encrypted.
   * @throws IOException If we are unable to read or create the directory, or if the directory is
   *     not a directory.
   */
  public PersistentTempFileBucketFactory(
      Path dir, final String prefix, Random weakPRNG, boolean encrypt) throws IOException {
    this.encrypt = encrypt;
    fg = new FilenameGenerator(weakPRNG, false, dir, prefix);
    if (!Files.exists(dir)) {
      try {
        Files.createDirectories(dir);
      } catch (Exception e) {
        throw new IOException("Directory does not exist and cannot be created: " + dir, e);
      }
    }
    if (!Files.isDirectory(dir)) {
      throw new IOException("Directory is not a directory: " + dir);
    }

    originalPaths = new HashSet<>();
    try (var stream =
        Files.newDirectoryStream(
            dir,
            entry ->
                Files.exists(entry)
                    && !Files.isDirectory(entry)
                    && entry.getFileName().toString().startsWith(prefix))) {

      for (Path path : stream) {
        var realPath = path.toRealPath();
        logger.info("Found {}", realPath);
        originalPaths.add(realPath);
      }
    } catch (IOException e) {
      logger.warn("Unable to list files in {}", dir, e);
    }

    bucketsToFree = new ArrayList<>();
    commitID = 1; // Must start > 0.
  }

  /**
   * Sets the {@link DiskSpaceChecker} to be used by this factory.
   *
   * <p>The {@link DiskSpaceChecker} is used to verify if there is sufficient disk space available
   * before writing to temporary files. This helps prevent disk space exhaustion and potential
   * system failures.
   *
   * @param checker The {@link DiskSpaceChecker} implementation to use. Must not be null.
   */
  public void setDiskSpaceChecker(DiskSpaceChecker checker) {
    this.checker = checker;
  }

  /**
   * Sets the master secret used for encrypting temporary files.
   *
   * <p>The master secret is used as a key derivation key to generate encryption keys for temporary
   * files. This method should be called after the master secret is initialized or loaded.
   *
   * <p><b>Synchronization:</b> This method is synchronized on {@link #encryptLock} to ensure
   * thread-safety when updating the secret, especially when encryption is enabled or disabled
   * concurrently.
   *
   * @param secret The {@link MasterSecret} to be used for encryption.
   */
  public void setMasterSecret(MasterSecret secret) {
    synchronized (encryptLock) {
      this.secret = secret;
    }
  }

  /**
   * Notify the bucket factory that a file is a temporary file, and should not be deleted during
   * startup cleanup.
   *
   * <p>This method is called during system resume to register files that are known to be valid
   * temporary files. Registered files are preserved during the startup cleanup process, which
   * removes any unregistered files found in the persistent-temp directory.
   *
   * <p><b>Important:</b> This method should be called for all persistent temporary files that are
   * expected to exist after a restart to prevent accidental deletion.
   *
   * @param path the {@link Path} to the file that needs to be registered. Must be the canonical
   *     path.
   * @throws IllegalStateException if {@link #completedInit()} has already been called, indicating
   *     that registration is no longer allowed.
   */
  @Override
  public void register(Path path) {
    synchronized (this) {
      if (originalPaths == null) {
        throw new IllegalStateException("completed Init has already been called!");
      }
      Path canonicalPath = FilePath.getCanonicalFile(path);
      logger.info("Preserving {}", canonicalPath, new Exception("debug"));

      if (!originalPaths.remove(canonicalPath)) {
        logger.error("Preserving {} but it wasn't found!", canonicalPath, new Exception("error"));
      }
    }
  }

  /** Called when boot-up is complete. Deletes any old temp files still unclaimed. */
  public synchronized void completedInit() {
    if (originalPaths == null) {
      logger.error("Completed init called twice", new Exception("error"));
      return;
    }
    for (Path path : originalPaths) {
      logger.info("Deleting old temp file {}", path);
      try {
        Files.delete(path);
      } catch (IOException e) {
        logger.warn("Unable to delete old temp file {}", path, e);
      }
    }
    originalPaths = null;
  }

  /**
   * Create a persistent temporary bucket.
   *
   * <p>This method creates a new {@link RandomAccessible} bucket that is backed by a persistent
   * temporary file. The bucket will be:
   *
   * <ul>
   *   <li><b>Persistent:</b> Data written to the bucket will be stored on disk and will survive
   *       node restarts.
   *   <li><b>Temporary:</b> The file is considered temporary and will be managed by the {@link
   *       PersistentFileTracker} for eventual disposal.
   *   <li><b>Encrypted (optionally):</b> If encryption is enabled for this factory, the bucket will
   *       be encrypted using an ephemeral key derived from the master secret.
   *   <li><b>Wrapped for delayed disposal:</b> The raw bucket is wrapped in a {@link
   *       DelayedDisposeRandomAccessBucket} to ensure that the underlying file is only deleted
   *       after the transaction committing the deletion has been written to disk.
   * </ul>
   *
   * @param size The suggested maximum size of the data in bytes. This is a hint and may not be
   *     strictly enforced.
   * @return A new {@link RandomAccessible} bucket instance, ready for writing data.
   * @throws IOException If there is an error creating the bucket or the underlying file.
   */
  @Override
  public RandomAccessible makeBucket(long size) throws IOException {
    RandomAccessible rawBucket = new PersistentTempFileBucket(fg.makeRandomFilename(), fg, this);

    synchronized (encryptLock) {
      if (encrypt) {
        rawBucket = new PaddedRandomAccessBucket(rawBucket);
        rawBucket = new EncryptedBucket(CRYPT_TYPE, rawBucket, secret);
      }
    }
    return new DelayedDisposeRandomAccessBucket(this, rawBucket);
  }

  /**
   * Schedules a bucket for disposal after the next disk serialization.
   *
   * <p>This method adds the given {@link DelayedDisposable} bucket to a list of buckets to be
   * disposed of. The actual disposal (deletion of the underlying file) is delayed until after the
   * next successful disk serialization (checkpoint). This ensures that the deletion is recorded in
   * the persistent state before the file is actually removed.
   *
   * <p><b>Commit ID Handling:</b> If the {@code createdCommitID} matches the current {@link
   * #commitID()}, the bucket is disposed of immediately. Otherwise, it is added to the {@link
   * #bucketsToFree} list and will be disposed of after the next commit. This mechanism handles
   * cases where buckets are created and deleted within the same transaction or across different
   * transactions.
   *
   * @param bucket The {@link DelayedDisposable} bucket to be disposed of. Must not be null.
   * @param createdCommitID The commit ID when the bucket was created. Use 0 for buckets created
   *     before the last node restart. If no commit has occurred since creation, the bucket can be
   *     disposed of immediately.
   */
  @Override
  public void delayedDispose(DelayedDisposable bucket, long createdCommitID) {
    synchronized (this) {
      if (createdCommitID != commitID()) {
        bucketsToFree.add(bucket);
        return;
      }
    }
    bucket.realDispose();
  }

  /**
   * Returns a list of buckets to dispose.
   *
   * <p>This method retrieves the list of buckets that are scheduled for disposal. It is intended to
   * be called during the checkpointing process. The caller should:
   *
   * <ol>
   *   <li>Write the array of {@link DelayedDisposable} buckets returned by this method to the
   *       checkpoint data.
   *   <li>After the checkpoint has been successfully written to disk, call {@link
   *       #finishDelayedFree(DelayedDisposable[])} to actually dispose of the buckets.
   * </ol>
   *
   * <p><b>Transaction Management:</b> This method increments the {@link #commitID()} to mark the
   * start of a new transaction. This ensures that subsequent calls to {@link
   * #delayedDispose(DelayedDisposable, long)} will correctly schedule buckets for disposal in the
   * next transaction.
   *
   * @return An array of {@link DelayedDisposable} buckets to be freed, or {@code null} if there are
   *     no buckets to free.
   */
  public DelayedDisposable[] grabBucketsToDispose() {
    synchronized (this) {
      if (bucketsToFree.isEmpty()) {
        return null;
      }
      DelayedDisposable[] buckets = bucketsToFree.toArray(new DelayedDisposable[0]);
      bucketsToFree.clear();
      commitID++;
      return buckets;
    }
  }

  /**
   * Returns the current transaction commit ID.
   *
   * <p>The commit ID is a monotonically increasing counter that is incremented with each
   * transaction commit (specifically, when {@link #grabBucketsToDispose()} is called). It serves as
   * a unique identifier for tracking file operations and ensuring consistent state across
   * transactions.
   *
   * @return A positive long number representing the current commit ID. Starts at 1 and increments
   *     with each commit.
   */
  @Override
  public synchronized long commitID() {
    return commitID;
  }

  /**
   * Retrieves the directory path for persistent temporary files.
   *
   * @return A {@link Path} object representing the directory where persistent temporary files are
   *     stored. This is the directory managed by the {@link FilenameGenerator} of this factory.
   */
  @Override
  public Path getDir() {
    return fg.getDir();
  }

  /**
   * Retrieves the filename generator for creating unique filenames.
   *
   * @return A {@link FilenameGenerator} instance used by this factory to generate unique filenames
   *     for persistent temporary files.
   */
  @Override
  public FilenameGenerator getGenerator() {
    return fg;
  }

  /**
   * Checks if encryption is enabled for temporary files created by this factory.
   *
   * <p><b>Synchronization:</b> Access to the {@link #encrypt} flag is synchronized using {@link
   * #encryptLock} to ensure thread-safe access, especially when the encryption setting is being
   * modified concurrently.
   *
   * @return {@code true} if encryption is enabled for new persistent temp buckets; {@code false}
   *     otherwise.
   */
  public boolean isEncrypting() {
    synchronized (encryptLock) {
      return encrypt;
    }
  }

  /**
   * Sets whether to encrypt new persistent temp buckets.
   *
   * <p>Note that this setting only affects buckets created <em>after</em> this method is called. It
   * does not encrypt or decrypt existing buckets.
   *
   * <p><b>Synchronization:</b> This method synchronizes on {@link #encryptLock} to ensure
   * thread-safe modification of the {@link #encrypt} flag.
   *
   * @param encrypt {@code true} to enable encryption for new buckets; {@code false} to disable it.
   */
  public void setEncryption(boolean encrypt) {
    synchronized (encryptLock) {
      this.encrypt = encrypt;
    }
  }

  /**
   * Disposes of the buckets that were scheduled for delayed disposal in the previous transaction.
   *
   * <p>This method is called after a checkpoint has been successfully written to disk. It iterates
   * through the array of {@link DelayedDisposable} buckets passed as argument and calls {@link
   * DelayedDisposable#realDispose()} on each bucket to actually delete the underlying temporary
   * files.
   *
   * <p><b>Error Handling:</b> Any exceptions thrown during the disposal of a bucket are caught and
   * logged, but do not prevent the disposal of other buckets in the array. This ensures that as
   * many temporary files as possible are cleaned up, even if some disposals fail.
   *
   * @param buckets An array of {@link DelayedDisposable} buckets to be disposed of. This array is
   *     typically obtained from a previous call to {@link #grabBucketsToDispose()}. Can be {@code
   *     null} or empty.
   */
  public void finishDelayedFree(DelayedDisposable[] buckets) {
    if (buckets != null) {
      for (DelayedDisposable bucket : buckets) {
        try {
          if (bucket.toDispose()) {
            bucket.realDispose();
          }
        } catch (Exception e) {
          logger.error("Caught {} freeing bucket {} after transaction commit", e, bucket, e);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation delegates the disk space check to the injected {@link DiskSpaceChecker}.
   */
  @Override
  public boolean checkDiskSpace(Path path, int toWrite, int bufferSize) {
    return checker.checkDiskSpace(path, toWrite, bufferSize);
  }

  /**
   * Filename generator.
   *
   * <p>This generator is responsible for:
   *
   * <ul>
   *   <li>Tracking the directory where temporary files are stored.
   *   <li>Managing the prefix used for temporary file names.
   *   <li>Handling file movement if the directory or prefix changes.
   *   <li>Generating unique filenames for new temporary files.
   * </ul>
   *
   * <p>It ensures that temporary files are named consistently and uniquely within the designated
   * directory.
   */
  private final FilenameGenerator fg;

  /**
   * Buckets to free.
   *
   * <p>This list holds {@link DelayedDisposable} buckets that are scheduled for disposal. Buckets
   * are added to this list when {@link #delayedDispose(DelayedDisposable, long)} is called. The
   * buckets in this list are disposed of in batch after a transaction commit, ensuring
   * transactional consistency of temporary file deletions.
   *
   * @see #delayedDispose(DelayedDisposable, long)
   * @see #grabBucketsToDispose()
   * @see #finishDelayedFree(DelayedDisposable[])
   */
  private final List<DelayedDisposable> bucketsToFree;

  /**
   * Lock object for synchronizing access to encryption-related fields.
   *
   * <p>This lock is used to protect concurrent access to the {@link #encrypt} flag and the {@link
   * #secret} master secret, ensuring thread-safety when enabling, disabling, or updating encryption
   * settings.
   */
  private final Object encryptLock = new Object();

  /**
   * Original contents of directory at startup.
   *
   * <p>This set stores the canonical paths of all files found in the persistent temporary file
   * directory at the time of factory initialization. It is used during the {@link #completedInit()}
   * phase to identify and delete any temporary files that were present in the directory at startup
   * but were not registered as active temporary files.
   *
   * <p>After the initial cleanup in {@link #completedInit()}, this field is set to {@code null} to
   * indicate that the cleanup process is complete and to prevent further modifications.
   */
  private @Nullable Set<Path> originalPaths;

  /**
   * Should we encrypt temporary files?
   *
   * <p>This flag determines whether new persistent temporary buckets created by this factory will
   * be encrypted. The setting can be changed at runtime via {@link #setEncryption(boolean)}.
   *
   * <p><b>Note:</b> This setting only affects buckets created <em>after</em> the flag is set.
   * Existing buckets are not retroactively encrypted or decrypted when this flag is changed.
   */
  private boolean encrypt;

  /**
   * Master secret for encryption.
   *
   * <p>This field holds the master secret used for deriving encryption keys for temporary files
   * when encryption is enabled. The master secret is set via {@link
   * #setMasterSecret(MasterSecret)}.
   *
   * <p><b>Important:</b> This field should only be accessed and modified under synchronization
   * using the {@link #encryptLock} to ensure thread-safety.
   */
  private MasterSecret secret;

  /**
   * Disk space checker.
   *
   * <p>This field holds the {@link DiskSpaceChecker} implementation that is used to verify
   * available disk space before writing to temporary files. It is injected via {@link
   * #setDiskSpaceChecker(DiskSpaceChecker)}.
   *
   * @see DiskSpaceChecker
   */
  private DiskSpaceChecker checker;

  /**
   * Commit ID.
   *
   * <p>This field stores the current transaction commit ID. It is incremented each time {@link
   * #grabBucketsToDispose()} is called, effectively marking the beginning of a new transaction. The
   * commit ID is used to track when buckets were created and ensure that delayed disposals are
   * handled correctly across transactions.
   *
   * @see #commitID()
   * @see #grabBucketsToDispose()
   * @see #delayedDispose(DelayedDisposable, long)
   */
  private long commitID;
}
