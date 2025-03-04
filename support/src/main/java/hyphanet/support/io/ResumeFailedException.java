package hyphanet.support.io;

import java.io.Serial;

public class ResumeFailedException extends Exception {
  @Serial private static final long serialVersionUID = 4332224721883071870L;

  public ResumeFailedException(String message) {
    super(message);
  }

  public ResumeFailedException(Throwable e) {
    super(e);
  }

  public ResumeFailedException(String message, Throwable e) {
    super(message, e);
  }
}
