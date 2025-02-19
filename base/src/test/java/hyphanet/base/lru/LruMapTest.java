/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package hyphanet.base.lru;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link LruMap} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
class LruMapTest {

  private static final int SAMPLE_ITEMS_NUMBER = 100;

  /**
   * Tests push(Object,Object) method providing null object as arguments (after setting up a sample
   * HashTable) and verifying if the correct exception is raised
   */
  @Test
  void testPushNull() {
    var methodLRUht = createSampleHashTable();

    final var obj = new Object();

    assertDoesNotThrow(
        () -> methodLRUht.addRecent(obj, null) // a null value is admitted
        );

    assertThrows(NullPointerException.class, () -> methodLRUht.addRecent(null, null));

    final var obj2 = new Object();

    assertThrows(
        NullPointerException.class,
        () -> methodLRUht.addRecent(null, obj2) // a null value is admitted
        );
  }

  /**
   * Tests push(Object,Object) method and verifies the behaviour when pushing the same object more
   * than one time.
   */
  @Test
  void testPushSameObjTwice() {
    var methodLRUht = createSampleHashTable();
    Object[][] sampleObj = {
      {SAMPLE_ITEMS_NUMBER, new Object()},
      {SAMPLE_ITEMS_NUMBER + 1, new Object()}
    };

    methodLRUht.addRecent(sampleObj[0][0], sampleObj[0][1]);
    methodLRUht.addRecent(sampleObj[1][0], sampleObj[1][1]);

    // check presence
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[0][0], sampleObj[0][1]));
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[1][0], sampleObj[1][1]));
    // check size
    assertEquals(SAMPLE_ITEMS_NUMBER + 2, methodLRUht.size());

    // push the same object another time
    methodLRUht.addRecent(sampleObj[0][0], sampleObj[0][1]);
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[0][0], sampleObj[0][1]));
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[1][0], sampleObj[1][1]));
    assertEquals(SAMPLE_ITEMS_NUMBER + 2, methodLRUht.size());
  }

  /**
   * Tests push(Object,Object) method and verifies the behaviour when pushing the same key with two
   * different values.
   */
  @Test
  void testPushSameKey() {
    var methodLRUht = createSampleHashTable();
    Object[][] sampleObj = {
      {SAMPLE_ITEMS_NUMBER, new Object()},
      {SAMPLE_ITEMS_NUMBER + 1, new Object()}
    };

    methodLRUht.addRecent(sampleObj[0][0], sampleObj[0][1]);
    methodLRUht.addRecent(sampleObj[1][0], sampleObj[1][1]);

    // check presence
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[0][0], sampleObj[0][1]));
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[1][0], sampleObj[1][1]));
    // check size
    assertEquals(SAMPLE_ITEMS_NUMBER + 2, methodLRUht.size());

    // creating and pushing a different value
    sampleObj[0][1] = new Object();
    methodLRUht.addRecent(sampleObj[0][0], sampleObj[0][1]);
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[0][0], sampleObj[0][1]));
    assertTrue(verifyKeyValPresence(methodLRUht, sampleObj[1][0], sampleObj[1][1]));
    assertEquals(SAMPLE_ITEMS_NUMBER + 2, methodLRUht.size());
  }

  /**
   * Tests popKey() method pushing and popping objects and verifying if their keys are correctly (in
   * a FIFO manner) fetched and the HashTable entry deleted
   */
  @Test
  void testPopKey() {
    var methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    // pushing objects
    for (Object[] sampleObject : sampleObjects)
      methodLRUht.addRecent(sampleObject[0], sampleObject[1]);
    // getting keys
    for (Object[] sampleObject : sampleObjects)
      assertEquals(sampleObject[0], methodLRUht.takeLeastRecentKey());
    // the HashTable must be empty
    assertNull(methodLRUht.takeLeastRecentKey());
  }

  /**
   * Tests popValue() method pushing and popping objects and verifying if their values are correctly
   * (in a FIFO manner) fetched and the HashTable entry deleted
   */
  @Test
  void testPopValue() {
    var methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    // pushing objects
    for (Object[] sampleObject : sampleObjects)
      methodLRUht.addRecent(sampleObject[0], sampleObject[1]);
    // getting values
    for (Object[] sampleObject : sampleObjects)
      assertEquals(sampleObject[1], methodLRUht.takeLeastRecentValue());
    // the HashTable must be empty
    assertNull(methodLRUht.takeLeastRecentValue());
  }

  /** Tests popValue() method popping a value from an empty LruMap. */
  @Test
  void testPopValueFromEmpty() {
    var methodLRUht = new LruMap<>();
    assertNull(methodLRUht.takeLeastRecentValue());
  }

  /**
   * Tests peekValue() method pushing and popping objects and verifying if their peekValue is
   * correct
   */
  @Test
  void testPeekValue() {
    var methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    // pushing objects
    for (Object[] sampleObject : sampleObjects)
      methodLRUht.addRecent(sampleObject[0], sampleObject[1]);
    // getting values
    for (Object[] sampleObject : sampleObjects) {
      assertEquals(sampleObject[1], methodLRUht.peekLeastRecentValue());
      methodLRUht.takeLeastRecentKey();
    }
    // the HashTable must be empty
    assertNull(methodLRUht.peekLeastRecentValue());
    // insert and fetch a null value
    methodLRUht.addRecent(new Object(), null);
    assertNull(methodLRUht.peekLeastRecentValue());
  }

  /** Tests size() method pushing and popping elements into the LruMap */
  @Test
  void testSize() {
    var methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    assertTrue(methodLRUht.isEmpty());
    // pushing objects
    for (int i = 0; i < sampleObjects.length; i++) {
      methodLRUht.addRecent(sampleObjects[i][0], sampleObjects[i][1]);
      assertEquals(methodLRUht.size(), i + 1);
    }
    // popping keys
    for (int i = sampleObjects.length - 1; i >= 0; i--) {
      methodLRUht.takeLeastRecentKey();
      assertEquals(methodLRUht.size(), i);
    }
  }

  /**
   * Tests removeKey(Object) method verifies if all elements are correctly removed checking the
   * method return value, if the element is still contained and the HashTable size.
   */
  @Test
  void testRemoveKey() {
    var methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    // pushing objects
    for (Object[] sampleObject : sampleObjects)
      methodLRUht.addRecent(sampleObject[0], sampleObject[1]);
    // popping keys
    for (int i = sampleObjects.length - 1; i >= 0; i--) {
      assertTrue(methodLRUht.removeKey(sampleObjects[i][0]));
      assertFalse(methodLRUht.containsKey(sampleObjects[i][0]));
      assertEquals(methodLRUht.size(), i);
    }
  }

  /**
   * Tests removeKey(Object) providing a null key and trying to remove it after setting up a sample
   * queue.
   */
  @Test
  void testRemoveNullKey() {
    var methodLRUht = createSampleHashTable();
    try {
      methodLRUht.removeKey(null);
      fail("Expected Exception Error Not Thrown!");
    } catch (NullPointerException anException) {
      assertNotNull(anException);
    }
  }

  /**
   * Tests removeKey(Object) method trying to remove a not present key after setting up a sample
   * LruMap.
   */
  @Test
  void testRemoveNotPresent() {
    var methodLRUht = createSampleHashTable();
    assertFalse(methodLRUht.removeKey(new Object()));
  }

  /**
   * Tests containsKey(Object) method trying to find a not present key after setting up a sample
   * queue. Then it search for a present one.
   */
  @Test
  void testContainsKey() {
    var methodLRUht = createSampleHashTable();
    assertFalse(methodLRUht.containsKey(new Object()));
    Object methodSampleObj = new Object();
    methodLRUht.addRecent(methodSampleObj, null);
    assertTrue(methodLRUht.containsKey(methodSampleObj));
  }

  /**
   * Tests get(Object) method trying to find a not present key after setting up a sample HashTable,
   * then it search a present key.
   */
  @Test
  void testGet() {
    var methodLRUht = createSampleHashTable();
    assertNull(methodLRUht.get(new Object()));
    Object methodSampleKey = new Object();
    Object methodSampleValue = new Object();
    methodLRUht.addRecent(methodSampleKey, methodSampleValue);
    assertEquals(methodLRUht.get(methodSampleKey), methodSampleValue);
  }

  /** Tests get(Object) trying to fetch a null key. */
  @Test
  void testGetNullKey() {
    var methodLRUht = createSampleHashTable();
    try {
      methodLRUht.get(null);
      fail("Expected Exception Error Not Thrown!");
    } catch (NullPointerException anException) {
      assertNotNull(anException);
    }
  }

  /** Tests keys() method verifying if the Enumeration provided is correct */
  @Test
  void testKeys() {
    LruMap<Object, Object> methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    // pushing objects
    for (Object[] sampleObject : sampleObjects)
      methodLRUht.addRecent(sampleObject[0], sampleObject[1]);
    var methodEnumeration = methodLRUht.keys();
    int j = 0;
    while (methodEnumeration.hasNext()) {
      assertEquals(methodEnumeration.next(), sampleObjects[j][0]);
      j++;
    }
  }

  /**
   * Tests isEmpty() method trying it with a new generated HashTable and after popping out all keys
   * in a sample LruMap
   */
  @Test
  void testIsEmpty() {
    LruMap<Object, Object> methodLRUht = new LruMap<>();
    assertTrue(methodLRUht.isEmpty());
    methodLRUht = createSampleHashTable();
    // popping keys
    for (int i = 0; i < SAMPLE_ITEMS_NUMBER; i++) methodLRUht.takeLeastRecentKey();
    assertTrue(methodLRUht.isEmpty());
  }

  /**
   * Creates a double array of objects with a specified size where Object[i][0] is the key, and is
   * an Integer, and Object[i][1] is the value
   *
   * @return the objects double array
   */
  private Object[][] createSampleKeyVal() {
    Object[][] sampleObjects = new Object[LruMapTest.SAMPLE_ITEMS_NUMBER][2];
    for (int i = 0; i < sampleObjects.length; i++) {
      // key
      sampleObjects[i][0] = i;
      // value
      sampleObjects[i][1] = new Object();
    }
    return sampleObjects;
  }

  /**
   * Creates a LruMap filled with the specified objects number
   *
   * @return the created LruMap
   */
  private LruMap<Object, Object> createSampleHashTable() {
    LruMap<Object, Object> methodLRUht = new LruMap<>();
    Object[][] sampleObjects = createSampleKeyVal();
    for (Object[] sampleObject : sampleObjects)
      methodLRUht.addRecent(sampleObject[0], sampleObject[1]);
    return methodLRUht;
  }

  /**
   * It verifies if a key-value pair is present in a LruMap
   *
   * @param aLRUht a LruMap to check in
   * @param aKey a key to find
   * @param aValue the correspondent value
   * @return true if the key is present and returned value is the same as in the argument
   */
  private boolean verifyKeyValPresence(LruMap<Object, Object> aLRUht, Object aKey, Object aValue) {
    if (aLRUht.containsKey(aKey)) return Objects.equals(aLRUht.get(aKey), aValue);
    return false;
  }
}
