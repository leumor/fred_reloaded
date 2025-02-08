package hyphanet.support;

public final class GlobalCleaner {
  // Create a single instance of hyphanet.support.io.Cleaner as a static final field.
  private static final java.lang.ref.Cleaner GLOBAL_CLEANER = java.lang.ref.Cleaner.create();

  // Private constructor prevents instantiation from other classes
  private GlobalCleaner() {}

  // Public method to provide access to the single hyphanet.support.io.Cleaner instance.
  public static java.lang.ref.Cleaner getInstance() {
    return GLOBAL_CLEANER;
  }
}
