package hyphanet.support.io.storage;

import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class TempStorageRamTracker {

  public void addToRamStorageQueue(TempStorage storage) {
    ramStorageQueue.add(new WeakReference<>(storage));
  }

  public void addToRamStorageQueue(WeakReference<TempStorage> storageRef) {
    ramStorageQueue.add(storageRef);
  }

  public void removeFromRamStorageQueue(WeakReference<TempStorage> storageRef) {
    ramStorageQueue.remove(storageRef);
  }

  public void takeRam(long size) {
    ramBytesInUse.addAndGet(size);
  }

  public void freeRam(long size) {
    ramBytesInUse.addAndGet(-size);
  }

  public Queue<WeakReference<TempStorage>> getRamStorageQueue() {
    return ramStorageQueue;
  }

  public long getRamBytesInUse() {
    return ramBytesInUse.get();
  }

  public boolean ramStorageRefInQueue(WeakReference<TempStorage> ref) {
    return ramStorageQueue.contains(ref);
  }

  private final Queue<WeakReference<TempStorage>> ramStorageQueue = new ConcurrentLinkedQueue<>();
  private final AtomicLong ramBytesInUse = new AtomicLong(0);
}
