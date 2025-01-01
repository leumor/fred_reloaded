package hyphanet.support.io.randomaccessbuffer;

import hyphanet.support.io.PersistentFileTracker;

/**
 * Defines the context required by a {@link RandomAccessBuffer} access buffer to perform
 * necessary actions after a restart or resumption of the application. This interface aims to
 * decouple {@link RandomAccessBuffer} implementations from specific application contexts,
 * promoting reusability.
 * <p>
 * Implementations of this interface provide the {@link RandomAccessBuffer} with access to
 * essential services or components required for its proper functioning after a restart. For
 * example, it might provide a way for the {@link RandomAccessBuffer} access buffer to register
 * itself with a persistent storage tracker.
 */
public interface ResumeContext {
    PersistentFileTracker getPersistentFileTracker();
}
