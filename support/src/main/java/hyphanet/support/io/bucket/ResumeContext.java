package hyphanet.support.io.bucket;

/**
 * Defines the context required by a {@link Bucket} to perform necessary actions after a
 * restart or resumption of the application. This interface aims to decouple {@link Bucket}
 * implementations from specific application contexts, promoting reusability.
 * <p>
 * Implementations of this interface provide the {@link Bucket} with access to essential
 * services or components required for its proper functioning after a restart. For example, it
 * might provide a way for the {@link Bucket} to register itself with a persistent storage
 * tracker.
 */
public interface ResumeContext {
    PersistentTempBucketFactory getPersistentBucketFactory();
}
