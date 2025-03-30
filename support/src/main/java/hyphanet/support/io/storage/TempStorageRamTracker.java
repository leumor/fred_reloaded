package hyphanet.support.io.storage;

import java.lang.ref.WeakReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

public class TempStorageRamTracker {

  public void addToRamStorageQueue(TempStorage storage) {
    ramStorageQueue.add(new WeakReference<>(storage));
  }

  public void addToRamStorageQueue(WeakReference<TempStorage> storageRef) {
    ramStorageQueue.add(storageRef);
  }

  public void removeFromRamStorageQueue(WeakReference<TempStorage> storageRef) {
    var found = searchRamStorageRefInQueue(storageRef);
    if (found != null) {
      ramStorageQueue.remove(found);
    }
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

  public @Nullable WeakReference<TempStorage> searchRamStorageRefInQueue(
      WeakReference<TempStorage> target) {
    WeakReference<TempStorage> found = null;

    // Use an Iterator to safely remove elements while iterating
    var iterator = ramStorageQueue.iterator();
    while (iterator.hasNext()) {
      var weakRef = iterator.next();
      var referent = weakRef.get(); // Get the strong reference (or null)

      if (referent == null) {
        // Referent was garbage collected, remove the stale WeakReference
        iterator.remove();
      } else if (referent == target.get()) {
        // Found the exact object instance!
        // We usually stop searching once found, but we could continue
        // iterating to clean the rest of the queue if desired.
        found = weakRef;
      }
    }
    return found;
  }

  public boolean ramStorageRefInQueue(WeakReference<TempStorage> target) {
    return searchRamStorageRefInQueue(target) != null;
  }

  private final Queue<WeakReference<TempStorage>> ramStorageQueue = new ConcurrentLinkedQueue<>();
  private final AtomicLong ramBytesInUse = new AtomicLong(0);
}
