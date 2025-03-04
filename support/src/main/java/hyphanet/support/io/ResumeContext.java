package hyphanet.support.io;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.PersistentTempFileBucketFactory;
import hyphanet.support.io.storage.rab.Rab;
import java.util.Random;

/**
 * Defines the context required by a {@link Rab} or {@link Bucket} to perform necessary actions
 * after a restart or resumption of the application. This interface aims to decouple {@link Rab} or
 * {@link Bucket} implementations from specific application contexts, promoting reusability.
 *
 * <p>Implementations of this interface provide the {@link Rab} or {@link Bucket} with access to
 * essential services or components required for its proper functioning after a restart. For
 * example, it might provide a way for the {@link Rab} or {@link Bucket} to register itself with a
 * persistent storage tracker.
 */
public interface ResumeContext {
  PersistentFileTracker getPersistentFileTracker();

  PersistentTempFileBucketFactory getPersistentTempBucketFactory();

  MasterSecret getPersistentMasterSecret();

  PersistentTempFileBucketFactory getPersistentBucketFactory();

  Random getFastWeakRandom();

  FilenameGenerator getPersistentFg();
}
