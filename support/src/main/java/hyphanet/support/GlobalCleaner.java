package hyphanet.support;

import java.lang.ref.Cleaner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton class responsible for managing {@link Cleaner.Cleanable} objects globally. It ensures
 * that all registered cleanup actions are invoked when the application exits gracefully. This
 * provides a central point for resource management that relies on the {@link Cleaner} mechanism.
 * Logging is performed using SLF4J.
 *
 * <p>WARNING: we should not track the monitored objects of a Cleaner.Cleanable instance. Otherwise,
 * the GlobalCleaner itself will hold a strong reference to the object, which will prevent the
 * object from ever becoming garbage collectible.
 */
@SuppressWarnings("java:S6548")
public final class GlobalCleaner {

  private static final Logger log = LoggerFactory.getLogger(GlobalCleaner.class);

  /** The underlying Cleaner instance used for registration. */
  private static final Cleaner CLEANER = Cleaner.create();

  /** Eagerly initialized singleton instance. */
  private static final GlobalCleaner GLOBAL_CLEANER = new GlobalCleaner();

  /**
   * Private constructor prevents instantiation from other classes and registers the shutdown hook.
   */
  private GlobalCleaner() {
    // Register a shutdown hook to clean up all registered resources when the JVM exits.
    Runtime.getRuntime()
        .addShutdownHook(new Thread(this::runShutdownCleanup, "GlobalCleaner-ShutdownHook"));
    log.info("Initialized and shutdown hook registered.");
  }

  /**
   * Returns the singleton instance of the GlobalCleaner.
   *
   * @return The single instance of GlobalCleaner.
   */
  public static GlobalCleaner getInstance() {
    return GLOBAL_CLEANER;
  }

  /**
   * Registers an object and a cleanup action with the global {@link Cleaner}. The returned {@link
   * Cleaner.Cleanable} is tracked by this GlobalCleaner.
   *
   * @param obj The object to monitor. When this object becomes phantom reachable, the action will
   *     be invoked.
   * @param action The cleanup action (a {@link Runnable}) to execute.
   * @return A {@link Cleaner.Cleanable} instance representing the registration.
   */
  public Cleaner.Cleanable register(Object obj, Runnable action) {
    if (obj == null || action == null) {
      throw new NullPointerException("Object and action must not be null");
    }
    // No need to wrap the action just for logging/removal here.
    // The core logic relies on unregister and shutdown hook.

    Cleaner.Cleanable cleanable = CLEANER.register(obj, action); // Pass original action
    registeredCleanables.add(cleanable);
    log.debug(
        "Registered cleanable for object {} (hash: {})",
        obj.getClass().getSimpleName(),
        System.identityHashCode(obj));
    return cleanable;
  }

  /**
   * Explicitly unregisters a {@link Cleaner.Cleanable} by invoking its cleanup action immediately
   * and removing it from the set of tracked cleanables.
   *
   * @param cleanable The {@link Cleaner.Cleanable} to unregister and clean. If null, the method
   *     does nothing.
   */
  public void clean(Cleaner.Cleanable cleanable) {
    if (cleanable == null) {
      return;
    }
    boolean removed = registeredCleanables.remove(cleanable);

    if (removed) {
      try {
        log.debug("Unregistering and cleaning resource via unregister().");
        cleanable.clean();
      } catch (Exception e) {
        log.error("Error during explicit unregister/clean:", e);
      }
    } else {
      log.trace(
          "Unregister called for a cleanable that was not found in the active set (might have been already cleaned/unregistered).");
    }
  }

  /**
   * Replaces an existing tracked {@link Cleaner.Cleanable} with a new registration for a given
   * object and action.
   *
   * <p>This method performs the following actions:
   *
   * <ol>
   *   <li>Attempts to remove {@code oldCleanable} from the tracking set.
   *   <li>If removal was successful, it immediately calls {@code oldCleanable.clean()}. Errors are
   *       logged.
   *   <li>Registers the {@code newObj} and {@code newAction} with the underlying {@link Cleaner},
   *       creating a {@code newCleanable}.
   *   <li>Adds the newly created {@code newCleanable} to the tracking set.
   * </ol>
   *
   * This ensures that the {@code oldCleanable}'s action is executed promptly (if it was tracked)
   * and the new registration is managed for shutdown cleanup.
   *
   * @param oldCleanable The previously returned cleanable to remove and clean immediately. Can be
   *     null.
   * @param newObj The new object to monitor for the replacement registration. Must not be null.
   * @param newAction The new cleanup action to associate with {@code newObj}. Must not be null.
   * @return The newly created and tracked {@link Cleaner.Cleanable} instance for the {@code
   *     newObj}/{@code newAction} pair.
   * @throws NullPointerException if newObj or newAction is null.
   */
  public Cleaner.Cleanable replace(
      Cleaner.Cleanable oldCleanable, Object newObj, Runnable newAction) {
    // Validate new inputs first
    if (newObj == null || newAction == null) {
      throw new NullPointerException("newObj and newAction must not be null for replacement");
    }

    // Handle the old cleanable (remove from tracking and clean)
    if (oldCleanable != null) {
      boolean oldWasRemoved = registeredCleanables.remove(oldCleanable);
      if (!oldWasRemoved) {
        log.warn(
            "Old cleanable {} provided to replace() was not found in the tracked set. Proceeding to register new cleanable.",
            oldCleanable);
      }
    } else {
      log.debug("oldCleanable was null in replace(). Proceeding to register new cleanable.");
    }

    // Create and register the new cleanable
    log.debug(
        "Replacing cleanable: Registering new object {} (hash: {})",
        newObj.getClass().getSimpleName(),
        System.identityHashCode(newObj));
    Cleaner.Cleanable newCleanable = CLEANER.register(newObj, newAction);

    // Add the new cleanable to tracking
    registeredCleanables.add(newCleanable);
    // No need to check add result usually, ConcurrentHashMap handles it. Log if needed.
    log.debug("Replacing cleanable: Added the new one {} to tracking.", newCleanable);

    // Return the newly created and tracked cleanable
    return newCleanable;
  }

  /**
   * Returns the current number of registered cleanables being tracked. Primarily for monitoring or
   * debugging.
   *
   * @return The count of active cleanables.
   */
  public int getRegisteredCleanableCount() {
    return registeredCleanables.size();
  }

  /**
   * The action performed by the shutdown hook. Iterates through tracked {@link Cleaner.Cleanable}s
   * and cleans them.
   */
  private void runShutdownCleanup() {
    if (!shuttingDown.compareAndSet(false, true)) {
      log.warn("Shutdown cleanup already in progress or completed. Skipping duplicate run.");
      return;
    }

    int initialCount = registeredCleanables.size(); // Get count before iteration
    log.info("Initiating shutdown cleanup for {} registered resources...", initialCount);
    int cleanCount = 0;
    int errorCount = 0;

    // Iterate over the set (safe with ConcurrentHashMap iterator)
    for (Cleaner.Cleanable cleanable : registeredCleanables) {
      try {
        cleanable.clean();
        cleanCount++;
      } catch (Exception e) {
        errorCount++;
        log.error("Error cleaning resource during shutdown:", e);
      }
    }

    // Clear the set after attempting cleanup
    registeredCleanables.clear();

    log.info("Shutdown cleanup finished. Attempted to clean {} resources.", cleanCount);
    if (errorCount > 0) {
      log.error("Encountered errors while cleaning {} resources during shutdown.", errorCount);
    }
    if (cleanCount < initialCount) {
      log.warn(
          "Shutdown cleanup attempted {} cleanups, but started with {} registered resources. "
              + "Some might have been cleaned concurrently or errors occurred.",
          cleanCount,
          initialCount);
    }
  }

  /** Thread-safe set to store all currently registered Cleanable objects. */
  private final Set<Cleaner.Cleanable> registeredCleanables = ConcurrentHashMap.newKeySet();

  /** Flag to prevent the shutdown hook from running multiple times. */
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
}
