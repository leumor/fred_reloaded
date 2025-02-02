package hyphanet.support.io;

public interface Resumable {

  /**
   * Called after restarting. The Storage should do any necessary housekeeping after resuming.
   *
   * @param context The necessary runtime support for resuming the Storage.
   * @throws ResumeFailedException If the resumption process encounters an error.
   * @see ResumeContext
   */
  default void onResume(ResumeContext context) throws ResumeFailedException {
    // Do nothing by default
  }
}
