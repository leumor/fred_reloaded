package hyphanet.support.io.storage;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;

public class TempStorageTracker {

  public void addToQueue(TempStorage storage) {
    storageQueue.add(storage);
  }

  public void removeFromQueue(TempStorage storage) {
    storageQueue.remove(storage);
  }

  public long getRamBytesInUse() {
    return storageQueue.stream()
        .filter(e -> !e.closed() && e.isRamStorage())
        .mapToLong(e -> e.getUnderlying().size())
        .reduce(0L, Long::sum);
  }

  public @Nullable TempStorage peakQueue() {
    return storageQueue.peek();
  }

  private final Queue<TempStorage> storageQueue = new ConcurrentLinkedQueue<>();
}
