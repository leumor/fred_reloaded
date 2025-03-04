package hyphanet.base.lru;

import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * {@code LruQueue} is a thread-safe implementation of a Least Recently Used (LRU) queue using
 * standard {@link LinkedList} and {@link HashMap}.
 *
 * <p><b>Ordering:</b>
 *
 * <ul>
 *   <li>The <b>head</b> (first element) of the {@link LinkedList} is the <b>most recently used
 *       (MRU)</b>.
 *   <li>The <b>tail</b> (last element) is the <b>least recently used (LRU)</b>.
 * </ul>
 *
 * <p><b>Functionality:</b>
 *
 * <ul>
 *   <li>{@link #addRecent(Object)} (push): Removes a duplicate (if present) and adds the object to
 *       the head (MRU).
 *   <li>{@link #addLeastRecent(Object)} (pushLeast): Removes a duplicate (if present) and adds the
 *       object to the tail (LRU).
 *   <li>{@link #takeLeastRecent()} (pop): Removes and returns the tail (LRU), returning {@code
 *       null} if the queue is empty.
 * </ul>
 *
 * <p>The {@link #toArrayOrdered()} and {@link #toArrayOrdered(Object[])} methods return an array
 * where index 0 contains the LRU element and the last element is the MRU. This is the reverse of
 * the internal LinkedList order to provide LRU-first ordering in the array representation.
 *
 * @param <T> The type of objects held in this {@code LruQueue}.
 */
public class LruQueue<T> {

  /**
   * Adds an object to the queue, making it the most recently used (MRU).
   *
   * <p>If the object is already present in the queue, it is moved to the front (MRU position). This
   * operation ensures that duplicate entries are avoided and the queue maintains LRU ordering.
   *
   * @param obj The object to add as the most recently used. Must not be {@code null}.
   * @throws NullPointerException if {@code obj} is {@code null}.
   */
  public final synchronized void addRecent(T obj) {
    if (obj == null) throw new NullPointerException();
    // If the element exists, remove it from its current position.
    if (map.containsKey(obj)) {
      list.remove(obj);
    }
    // Add the element at the front (MRU) and update the map.
    list.addFirst(obj);
    map.put(obj, obj);
  }

  /**
   * Adds an object to the queue, making it the least recently used (LRU).
   *
   * <p>If the object is already present in the queue, it is moved to the back (LRU position). This
   * is useful for preloading or adding elements that are expected to be used less frequently.
   *
   * @param obj The object to add as the least recently used. Must not be {@code null}.
   * @throws NullPointerException if {@code obj} is {@code null}.
   */
  public synchronized void addLeastRecent(T obj) {
    if (obj == null) throw new NullPointerException();
    if (map.containsKey(obj)) {
      list.remove(obj);
    }
    // Add the element at the tail (LRU) and update the map.
    list.addLast(obj);
    map.put(obj, obj);
  }

  /**
   * Removes and returns the least recently used object from the queue.
   *
   * <p>This operation effectively "pops" the LRU element from the queue. If the queue is empty, it
   * returns {@code null}.
   *
   * @return The least recently used object, or {@code null} if the queue is empty.
   */
  public final synchronized @Nullable T takeLeastRecent() {
    if (!list.isEmpty()) {
      T obj = list.removeLast(); // remove LRU from tail
      map.remove(obj);
      return obj;
    } else {
      return null;
    }
  }

  /**
   * Returns the number of elements currently in the queue.
   *
   * @return The size of the queue.
   */
  public final synchronized int size() {
    return list.size();
  }

  /**
   * Removes the specified object from the queue if it is present.
   *
   * <p>If the object is found and removed, the LRU ordering is maintained.
   *
   * @param obj The object to remove. Must not be {@code null}.
   * @return {@code true} if the object was removed, {@code false} otherwise.
   * @throws NullPointerException if {@code obj} is {@code null}.
   */
  public final synchronized boolean remove(T obj) {
    if (obj == null) throw new NullPointerException();
    if (map.containsKey(obj)) {
      map.remove(obj);
      list.remove(obj);
      return true;
    }
    return false;
  }

  /**
   * Checks if the queue contains the specified object.
   *
   * <p>This operation leverages the {@link HashMap} for efficient lookups.
   *
   * @param obj The object to check for.
   * @return {@code true} if the queue contains the object, {@code false} otherwise.
   */
  public final synchronized boolean contains(T obj) {
    return map.containsKey(obj);
  }

  /**
   * Returns an {@link Iterator} over the elements in the queue, ordered from LRU to MRU.
   *
   * <p>This iterator allows traversal of the queue elements in the least-recently-used to
   * most-recently-used order.
   *
   * @return An {@link Iterator} of the objects in the queue from LRU to MRU.
   */
  public Iterator<T> elements() {
    return list.descendingIterator();
  }

  /**
   * Returns an array containing all the objects in the queue in an arbitrary order.
   *
   * <p>The order of elements in the returned array is not guaranteed to be related to LRU order. If
   * ordered array is required, use {@link #toArrayOrdered()} instead.
   *
   * @return An array containing all the objects in the queue.
   * @see #toArrayOrdered()
   */
  public synchronized Object[] toArray() {
    return map.keySet().toArray();
  }

  /**
   * Returns an array containing all the objects in the queue in an arbitrary order.
   *
   * <p>The order of elements in the returned array is not guaranteed to be related to LRU order. If
   * ordered array is required, use {@link #toArrayOrdered()} instead.
   *
   * @param array The array into which the elements of the queue are to be stored, if it is big
   *     enough; otherwise, a new array of the same runtime type is allocated for this purpose.
   * @param <E> The element type of the array.
   * @return An array containing all the objects in the queue.
   * @throws ArrayStoreException if the runtime type of the specified array is not a supertype of
   *     the runtime type of every element in this queue.
   * @throws NullPointerException if the specified array is {@code null}.
   * @see #toArrayOrdered(E[])
   */
  public synchronized <E> E[] toArray(E[] array) {
    return map.keySet().toArray(array);
  }

  /**
   * Returns an array containing all the objects in the queue, ordered from LRU to MRU.
   *
   * <p>The element at index 0 of the returned array is the least recently used, and the last
   * element is the most recently used.
   *
   * @return An array containing the objects in LRU to MRU order.
   */
  public synchronized Object[] toArrayOrdered() {
    Object[] array = new Object[list.size()];
    int index = 0;
    // Using descendingIterator so that iteration goes from tail (LRU) to head (MRU)
    for (Iterator<T> it = list.descendingIterator(); it.hasNext(); ) {
      array[index++] = it.next();
    }
    return array;
  }

  /**
   * Returns an array containing all the objects in the queue, ordered from LRU to MRU.
   *
   * <p>The element at index 0 of the returned array is the least recently used, and the last
   * element is the most recently used.
   *
   * <p><b>Precondition:</b>
   *
   * <p>The provided {@code array} must have a length equal to the current size of the queue. If the
   * array length does not match the queue size, an {@link IllegalStateException} is thrown.
   *
   * @param array The array to fill with the queue elements in LRU to MRU order. Must have a length
   *     equal to the current size of the queue.
   * @param <E> The element type of the array.
   * @return The input {@code array} populated with the objects in LRU to MRU order.
   * @throws IllegalStateException if {@code array.length} is not equal to the queue's size.
   * @throws ArrayStoreException if the runtime type of the specified array is not a supertype of
   *     the runtime type of every element in this queue.
   * @throws NullPointerException if the specified array is {@code null}.
   */
  @SuppressWarnings("unchecked")
  public synchronized <E> E[] toArrayOrdered(E[] array) {
    if (array.length != list.size())
      throw new IllegalStateException(
          "array.length=" + array.length + " but list.size=" + list.size());
    int index = 0;
    for (Iterator<T> it = list.descendingIterator(); it.hasNext(); ) {
      array[index++] = (E) it.next();
    }
    return array;
  }

  /**
   * Checks if the queue is empty.
   *
   * @return {@code true} if the queue contains no elements, {@code false} otherwise.
   */
  public synchronized boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Removes all elements from the queue, making it empty.
   *
   * <p>This operation clears both the internal {@link LinkedList} and {@link HashMap}.
   */
  public synchronized void clear() {
    list.clear();
    map.clear();
  }

  /**
   * Returns the object associated with the given object in the queue, or {@code null} if it is not
   * present.
   *
   * <p>In this implementation, the object itself is used as the key in the {@link HashMap}. This
   * method effectively checks for the presence of the object and returns it if found. Since the
   * value in the map is the object itself, this method essentially returns the object if it exists.
   *
   * @param obj The object to retrieve from the queue.
   * @return The object if it is in the queue, {@code null} otherwise.
   */
  public synchronized @Nullable T get(T obj) {
    return map.get(obj);
  }

  /**
   * The {@link ArrayDeque} that maintains the LRU order of elements.
   *
   * <p>The head of the list is the most recently used element, and the tail is the least recently
   * used element.
   */
  private final ArrayDeque<T> list = new ArrayDeque<>();

  /**
   * The {@link HashMap} used for fast lookups and presence checks of elements in the queue.
   *
   * <p>Maps each element in the queue to itself, allowing for O(1) time complexity for {@link
   * #contains(Object)} and {@link #remove(Object)} operations.
   */
  private final Map<T, T> map = new HashMap<>();
}
