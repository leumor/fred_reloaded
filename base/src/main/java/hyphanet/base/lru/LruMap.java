package hyphanet.base.lru;

import java.util.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An LRU map from K to V. That is, when a mapping is added, it is pushed to the top of the queue,
 * even if it was already present, and pop/peek operate from the bottom of the queue i.e. the least
 * recently pushed. The caller must implement any size limit needed. FIXME most callers should be
 * switched to LinkedHashMap. Does not support null keys.
 *
 * @param <K> The key type.
 * @param <V> The value type.
 */
/**
 * An LRU (Least Recently Used) map from keys of type {@code K} to values of type {@code V}.
 *
 * <p>This map maintains a queue of entries ordered by their last access time. When a key-value pair
 * is added using {@link #addRecent(K, V)}, it is considered the most recently used and placed at
 * the front of the queue. If the key already exists, its associated value is updated, and it's
 * moved to the front of the queue, effectively making it the most recently used.
 *
 * <p>Operations like retrieving the least recently used key or value (e.g., {@link
 * #takeLeastRecentKey()}, {@link #peekLeastRecentValue()}) operate on the entries at the back of
 * the queue, which represent the least recently used items.
 *
 * <p><b>Important:</b> This implementation does <b>not</b> enforce a size limit. It is the caller's
 * responsibility to manage the size of the map based on their requirements.
 *
 * <p><b>Limitations:</b> This map does not support {@code null} keys and will throw a {@link
 * NullPointerException} if {@code null} is used as a key.
 *
 * @param <K> The type of keys in this map.
 * @param <V> The type of values in this map.
 */
public class LruMap<K, V> {
  private static final Logger logger = LoggerFactory.getLogger(LruMap.class);

  /**
   * Constructs a new empty {@link LruMap} using a {@link HashMap} as the underlying map.
   *
   * <p>This constructor creates an {@link LruMap} that is suitable for general use cases where key
   * ordering is not critical for security and hash collisions are not a major concern.
   */
  public LruMap() {
    underlyingMap = new HashMap<>();
  }

  /**
   * Constructs a new {@link LruMap} with a specified underlying map implementation.
   *
   * <p>This constructor is intended for internal use and for creating specialized {@link LruMap}
   * instances, such as those based on {@link TreeMap} for security against hash collision attacks.
   *
   * @param underlyingMap The map to be used internally to store key-value pairs.
   */
  private LruMap(Map<K, QItem<K, V>> underlyingMap) {
    this.underlyingMap = underlyingMap;
  }

  /**
   * Creates a new {@link LruMap} that is safe to use with keys potentially controlled by an
   * attacker.
   *
   * <p>This method initializes the {@link LruMap} with a {@link TreeMap} as the underlying map.
   * Using a {@link TreeMap} instead of a {@link HashMap} mitigates the risk of hash collision
   * denial-of-service (DoS) attacks, as {@link TreeMap} relies on key comparison rather than
   * hashing, making it less vulnerable to crafted hash collisions.
   *
   * <p>This method is suitable when keys are {@link Comparable}.
   *
   * @param <K> The type of keys in this map, must be {@link Comparable}.
   * @param <V> The type of values in this map.
   * @return A new {@code LruMap} instance backed by a {@link TreeMap}.
   */
  public static <K extends Comparable<? super K>, V> LruMap<K, V> createSafeMap() {
    return new LruMap<>(new TreeMap<>());
  }

  /**
   * Creates a new {@link LruMap} that is safe to use with keys potentially controlled by an
   * attacker, using a custom comparator.
   *
   * <p>This method is similar to {@link #createSafeMap()}, but it allows specifying a custom {@link
   * Comparator} for key ordering in the underlying {@link TreeMap}. This is useful when the keys
   * are not naturally {@link Comparable} or when a specific ordering is required.
   *
   * @param <K> The type of keys in this map.
   * @param <V> The type of values in this map.
   * @param comparator The comparator to determine the order of keys in the {@link TreeMap}.
   * @return A new {@code LruMap} instance backed by a {@link TreeMap} with the given comparator.
   */
  public static <K, V> LruMap<K, V> createSafeMap(Comparator<? super K> comparator) {
    return new LruMap<>(new TreeMap<>(comparator));
  }

  /**
   * Adds or updates a key-value mapping in the LRU map, marking the key as the most recently used.
   *
   * <p>If the key is already present in the map, its associated value is updated to the new value,
   * and the entry is moved to the front of the LRU queue, making it the most recently used. This
   * effectively "refreshes" the entry's position in the LRU queue.
   *
   * <p>If the key is not already present, a new entry is created with the given key and value, and
   * it is added to the front of the LRU queue.
   *
   * @param key The key to add or update; must not be {@code null}.
   * @param value The value to associate with the key.
   * @return The previous value associated with the key, or {@code null} if the key was not already
   *     present.
   * @throws NullPointerException if the key is {@code null}.
   */
  public final synchronized V addRecent(K key, V value) {
    if (key == null) throw new NullPointerException();
    V old = null;
    var item = underlyingMap.get(key);
    if (item != null) {
      old = item.value;
      item.value = value;
      list.remove(item); // Remove it from its current position.
    } else {
      item = new QItem<>(key, value);
      underlyingMap.put(key, item);
    }
    logger.info("Pushed {} ( {} {} )", item, key, value);

    list.addFirst(item); // Add to head (most recent)
    return old;
  }

  /**
   * Removes and returns the least recently used key from the LRU map.
   *
   * <p>This operation removes the key-value mapping associated with the least recently used key
   * from both the LRU queue and the underlying map. If the map is empty, it returns {@code null}.
   *
   * @return The least recently used key, or {@code null} if the map is empty.
   */
  public final synchronized @Nullable K takeLeastRecentKey() {
    if (!list.isEmpty()) {
      var item = list.removeLast(); // Least recent at tail.
      underlyingMap.remove(item.key);
      return item.key;
    }
    return null;
  }

  /**
   * Removes and returns the value associated with the least recently used key from the LRU map.
   *
   * <p>This operation is similar to {@link #takeLeastRecentKey()}, but it returns the value instead
   * of the key. It removes the least recently used entry from both the LRU queue and the underlying
   * map. If the map is empty, it returns {@code null}.
   *
   * @return The value associated with the least recently used key, or {@code null} if the map is
   *     empty.
   */
  public final synchronized @Nullable V takeLeastRecentValue() {
    if (!list.isEmpty()) {
      QItem<K, V> item = list.removeLast();
      underlyingMap.remove(item.key);
      return item.value;
    }
    return null;
  }

  /**
   * Returns the least recently used key from the LRU map without removing it.
   *
   * <p>This method allows you to inspect the least recently used key without modifying the map. If
   * the map is empty, it returns {@code null}.
   *
   * @return The least recently used key, or {@code null} if the map is empty.
   */
  public final synchronized @Nullable K peekLeastRecentKey() {
    if (!list.isEmpty()) {
      return list.getLast().key;
    }
    return null;
  }

  /**
   * Returns the value associated with the least recently used key from the LRU map without removing
   * it.
   *
   * <p>This method is similar to {@link #peekLeastRecentKey()}, but it returns the value instead of
   * the key. It allows you to inspect the value of the least recently used entry without modifying
   * the map. If the map is empty, it returns {@code null}.
   *
   * @return The value associated with the least recently used key, or {@code null} if the map is
   *     empty.
   */
  public final synchronized @Nullable V peekLeastRecentValue() {
    if (!list.isEmpty()) {
      return list.getLast().value;
    }
    return null;
  }

  /**
   * Returns the number of key-value mappings in this LRU map.
   *
   * <p>This method returns the current size of the LRU map, which is equivalent to the number of
   * entries in the LRU queue.
   *
   * @return The number of key-value mappings in this map.
   */
  public final int size() {
    return list.size();
  }

  /**
   * Removes the mapping for a key from this LRU map if it is present.
   *
   * <p>If a mapping exists for the given key, it is removed from both the underlying map and the
   * LRU queue. If the key is not present, the map is not modified.
   *
   * @param key The key whose mapping is to be removed from the map; must not be {@code null}.
   * @return {@code true} if a mapping was removed, {@code false} otherwise.
   * @throws NullPointerException if the key is {@code null}.
   */
  public final synchronized boolean removeKey(K key) {
    if (key == null) throw new NullPointerException();
    QItem<K, V> i = underlyingMap.remove(key);
    if (i != null) {
      list.remove(i);
      return true;
    }
    return false;
  }

  /**
   * Checks if this LRU map contains a mapping for the specified key.
   *
   * <p>This method determines if the given key is present in the map. It does not modify the LRU
   * order of the keys.
   *
   * @param key The key to search for; must not be {@code null}.
   * @return {@code true} if this map contains a mapping for the specified key, {@code false}
   *     otherwise.
   * @throws NullPointerException if the key is {@code null}.
   */
  public final synchronized boolean containsKey(K key) {
    if (key == null) throw new NullPointerException();
    return underlyingMap.containsKey(key);
  }

  /**
   * Returns the value to which the specified key is mapped, or {@code null} if this map contains no
   * mapping for the key.
   *
   * <p><b>Important:</b> Unlike standard LRU cache implementations, this {@code get} operation does
   * <b>not</b> automatically promote the accessed key to the most recently used position. To update
   * the LRU order upon access, you must explicitly call {@link #addRecent(K, V)} with the retrieved
   * key and value after calling {@code get}.
   *
   * @param key The key whose associated value is to be returned; must not be {@code null}.
   * @return The value to which the specified key is mapped, or {@code null} if this map contains no
   *     mapping for the key.
   * @throws NullPointerException if the key is {@code null}.
   */
  public final synchronized @Nullable V get(K key) {
    if (key == null) throw new NullPointerException();
    QItem<K, V> item = underlyingMap.get(key);
    return item == null ? null : item.value;
  }

  /**
   * Returns an iterator over the keys in this LRU map, ordered from least recently used to most
   * recently used.
   *
   * <p>The iterator provides a snapshot of the keys in the LRU map at the time the iterator is
   * created. The keys are returned in the order of their last access, with the least recently used
   * key appearing first and the most recently used key appearing last.
   *
   * <p><b>Note:</b> The returned iterator is fail-fast.
   *
   * @return An iterator over the keys in this LRU map, in least-recently-used to most-recently-used
   *     order.
   */
  public synchronized Iterator<K> keys() {
    List<K> snapshot = new ArrayList<>(list.size());
    // Using the descendingIterator to get keys from tail (eldest) to head (most recent)
    Iterator<QItem<K, V>> it = list.descendingIterator();
    while (it.hasNext()) {
      snapshot.add(it.next().key);
    }
    return snapshot.iterator();
  }

  /**
   * Returns an iterator over the values in this LRU map, ordered from least recently used to most
   * recently used key.
   *
   * <p>The iterator provides a snapshot of the values in the LRU map at the time the iterator is
   * created. The values are returned in the order corresponding to the LRU order of their keys,
   * with the value associated with the least recently used key appearing first and the value
   * associated with the most recently used key appearing last.
   *
   * <p><b>Note:</b> The returned iterator is fail-fast.
   *
   * @return An iterator over the values in this LRU map, in least-recently-used to
   *     most-recently-used order.
   */
  public synchronized Iterator<V> values() {
    List<V> snapshot = new ArrayList<>(list.size());
    Iterator<QItem<K, V>> it = list.descendingIterator();
    while (it.hasNext()) {
      snapshot.add(it.next().value);
    }
    return snapshot.iterator();
  }

  /**
   * Returns {@code true} if this LRU map contains no key-value mappings.
   *
   * <p>This method checks if the map is empty.
   *
   * @return {@code true} if this LRU map contains no key-value mappings, {@code false} otherwise.
   */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /**
   * Copies the values of this LRU map into the specified array, in least-recently-used to
   * most-recently-used order.
   *
   * <p><b>Important:</b> Unlike standard {@code toArray()} methods in {@code java.util}
   * collections, this method does <b>not</b> create a new array. It requires the caller to provide
   * an array of sufficient size to hold all the values in the map. If the provided array is not
   * large enough, an {@link ArrayIndexOutOfBoundsException} may occur.
   *
   * @param entries The array into which the values of the LRU map are to be copied. Must be
   *     pre-allocated and of sufficient size.
   * @throws ArrayIndexOutOfBoundsException if the provided array is not large enough to hold all
   *     the values.
   */
  public synchronized void valuesToArray(V[] entries) {
    int i = 0;
    Iterator<QItem<K, V>> iter = list.descendingIterator();
    while (iter.hasNext()) {
      entries[i++] = iter.next().value;
    }
  }

  /**
   * Removes all mappings from this LRU map.
   *
   * <p>This operation clears both the LRU queue and the underlying map, effectively making the LRU
   * map empty.
   */
  public synchronized void clear() {
    list.clear();
    underlyingMap.clear();
  }

  /**
   * Private inner class representing an item in the LRU queue.
   *
   * <p>Each {@link QItem} holds a key-value pair and is used internally by {@link LruMap} to
   * maintain the LRU order.
   *
   * @param <K> The type of the key.
   * @param <V> The type of the value.
   */
  private static class QItem<K, V> {
    /**
     * Constructs a new {@link QItem} with the specified key and value.
     *
     * @param key The key of the item.
     * @param value The value of the item.
     */
    public QItem(K key, V value) {
      this.key = key;
      this.value = value;
    }

    /**
     * Returns a string representation of this {@link QItem}.
     *
     * @return A string representation in the format "key value".
     */
    @Override
    public String toString() {
      return key + " " + value;
    }

    /** The key of this item. */
    K key;

    /** The value of this item. */
    V value;
  }

  /**
   * Array deque to maintain the LRU order of {@link QItem}s.
   *
   * <p>The list stores {@link QItem}s in the order of their last access, with the most recently
   * used item at the head and the least recently used item at the tail. Using a {@link ArrayDeque}
   * allows for efficient addition and removal of items at both ends.
   */
  private final ArrayDeque<QItem<K, V>> list = new ArrayDeque<>();

  /**
   * Underlying map to store key to {@link QItem} mappings for fast lookup.
   *
   * <p>This map provides constant-time average complexity for operations like {@code get}, {@code
   * put}, and {@code remove} based on keys. The values in this map are {@link QItem} instances,
   * which are also stored in the {@link #list} to maintain LRU order.
   */
  private final Map<K, QItem<K, V>> underlyingMap;
}
