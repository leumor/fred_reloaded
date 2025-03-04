/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.base.lru;

import org.jspecify.annotations.Nullable;

/**
 * A key-value cache with a fixed size and optional expiration time. The cache evicts the least
 * recently used item when it reaches its size limit and a new entry is added.
 *
 * <p>Existing entries are returned by {@link #get(Comparable)} only if they are not expired.
 *
 * <p>Expired items are passively removed in two scenarios:
 *
 * <ol>
 *   <li>When {@link #get(Comparable)} is called for an expired entry.
 *   <li>When an expired item becomes the least-recently-used during a {@link #put(Comparable,
 *       Object)} operation because the cache is full.
 * </ol>
 *
 * <p>This passive expiration strategy makes this cache suitable for:
 *
 * <ul>
 *   <li>Small caches where frequent garbage collection is not desired.
 *   <li>Caches containing a large number of small items where the overhead of active expiration
 *       management is not justified.
 * </ul>
 *
 * <p>The {@link #put(Comparable, Object)} and {@link #get(Comparable)} operations are performed in
 * O(lg N) time complexity, leveraging a tree-based {@link LruMap} implementation to mitigate
 * potential hash collision denial-of-service attacks.
 *
 * @param <K> The type of keys in the cache, must be {@link Comparable} to allow for tree-based map
 *     implementation.
 * @param <V> The type of values in the cache.
 * @author xor (xor@freenetproject.org)
 */
public final class LruCache<K extends Comparable<K>, V> {

  /**
   * Creates a cache without an expiration time.
   *
   * @param sizeLimit The maximum number of items the cache can hold.
   */
  public LruCache(final int sizeLimit) {
    cache = LruMap.createSafeMap();
    this.sizeLimit = sizeLimit;
    expirationDelay = Long.MAX_VALUE;
  }

  /**
   * Creates a cache with a specified size limit and expiration time.
   *
   * @param sizeLimit The maximum number of items the cache can hold.
   * @param expirationDelay The time in milliseconds after which an entry expires.
   */
  public LruCache(final int sizeLimit, final long expirationDelay) {
    cache = LruMap.createSafeMap();
    this.sizeLimit = sizeLimit;
    this.expirationDelay = expirationDelay;
  }

  /**
   * Puts a value into the cache under the given key.
   *
   * <p>If an entry with the given key already exists:
   *
   * <ul>
   *   <li>Its value is updated to the new value.
   *   <li>Its expiration time is reset, effectively extending its lifespan in the cache.
   *   <li>It is moved to the most recently used position in the cache.
   * </ul>
   *
   * <p>If the cache is already at its {@link #sizeLimit} and a new entry is added, the least
   * recently used entry is removed to make space.
   *
   * @param key The key under which to store the value. Must not be {@code null}.
   * @param value The value to be stored in the cache.
   * @throws NullPointerException if the key is {@code null}.
   */
  public void put(final K key, final V value) {
    cache.addRecent(key, new Entry(value));
    freeCapacity();
  }

  /**
   * Retrieves a value from the cache associated with the given key.
   *
   * <p>If an entry is found and is not expired:
   *
   * <ul>
   *   <li>The entry is moved to the most recently used position in the cache, updating its position
   *       in the LRU order.
   *   <li>The associated value is returned.
   * </ul>
   *
   * <p>If no entry is found for the key, or if the entry is expired:
   *
   * <ul>
   *   <li>If an expired entry is found, it is removed from the cache.
   *   <li>{@code null} is returned.
   * </ul>
   *
   * @param key The key of the entry to retrieve. Must not be {@code null}.
   * @return The value associated with the key if found and not expired, otherwise {@code null}.
   * @throws NullPointerException if the key is {@code null}.
   */
  public @Nullable V get(final K key) {
    Entry entry = cache.get(key);
    if (entry == null) {
      return null;
    }

    if (expirationDelay < Long.MAX_VALUE && entry.expired()) {
      cache.removeKey(key);
      return null;
    }

    cache.addRecent(key, entry); // Move the key to top.

    return entry.getValue();
  }

  /**
   * Clears all entries from the cache, effectively making it empty. This operation removes all
   * key-value mappings, regardless of their expiration status or LRU position.
   */
  public void clear() {
    cache.clear();
  }

  /**
   * Removes the least recently used items from the cache until the cache size is within the {@link
   * #sizeLimit}.
   *
   * <p>This method is called internally by {@link #put(Comparable, Object)} to enforce the size
   * limit of the cache. It iteratively removes the least recently used entry until the number of
   * entries in the cache is no longer greater than {@link #sizeLimit}. The removal process is
   * handled by the underlying {@link LruMap}.
   */
  private void freeCapacity() {
    while (cache.size() > sizeLimit) {
      cache.takeLeastRecentValue();
    }
  }

  /**
   * Private inner class representing an entry in the {@link LruCache}. Each entry holds a value and
   * an expiration date.
   */
  private final class Entry {
    /**
     * Constructs a new cache entry with the given value and calculates its expiration date based on
     * the cache's {@link #expirationDelay}.
     *
     * @param myValue The value to be stored in this entry.
     */
    public Entry(final V myValue) {
      value = myValue;
      expirationDate =
          (expirationDelay < Long.MAX_VALUE)
              ? (System.currentTimeMillis() + expirationDelay)
              : Long.MAX_VALUE;
    }

    /**
     * Checks if this entry is expired at the given time.
     *
     * @param time The time to check against, typically {@link System#currentTimeMillis()}.
     * @return {@code true} if the entry's expiration date is before the given time, {@code false}
     *     otherwise.
     */
    public boolean expired(final long time) {
      return expirationDate < time;
    }

    /**
     * Checks if this entry is currently expired based on the current system time.
     *
     * @return {@code true} if the entry is expired, {@code false} otherwise.
     * @see #expired(long)
     */
    public boolean expired() {
      return expired(System.currentTimeMillis());
    }

    /**
     * Returns the value stored in this entry.
     *
     * @return The value of the entry.
     */
    public V getValue() {
      return value;
    }

    /** The value stored in this cache entry. */
    private final V value;

    /**
     * The expiration date of this cache entry, represented as milliseconds since the epoch. Entries
     * are considered expired if the current time is after this date. A value of {@link
     * Long#MAX_VALUE} indicates that the entry never expires.
     */
    private final long expirationDate;
  }

  /**
   * The maximum number of entries this cache can hold. Once the cache reaches this size, adding new
   * entries will cause the least recently used entry to be evicted.
   */
  private final int sizeLimit;

  /**
   * The expiration delay for entries in this cache, in milliseconds. Entries older than this delay
   * since their last access (or creation) are considered expired. A value of {@link Long#MAX_VALUE}
   * indicates no expiration.
   */
  private final long expirationDelay;

  /**
   * The underlying {@link LruMap} used to store the cache entries. It manages the LRU ordering and
   * provides efficient access and removal of entries. The keys are of type {@code K} and the values
   * are {@link Entry} objects.
   */
  private final LruMap<K, Entry> cache;
}
