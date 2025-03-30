package hyphanet.support.io.storage;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractStorage implements Storage {

  @Override
  public void close() {
    setClosed();
  }

  @Override
  public void dispose() {
    setClosed();
    setDisposed();
  }

  @Override
  public boolean closed() {
    return closed.get();
  }

  @Override
  public boolean disposed() {
    return disposed.get();
  }

  protected boolean setClosed() {
    return closed.compareAndSet(false, true);
  }

  protected boolean setDisposed() {
    return disposed.compareAndSet(false, true);
  }

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean disposed = new AtomicBoolean(false);
}
