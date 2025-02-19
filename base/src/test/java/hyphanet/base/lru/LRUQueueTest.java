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

import org.junit.jupiter.api.Test;

/**
 * Test case for {@link LruQueue} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
class LRUQueueTest {

  private static final int SAMPLE_ITEMS_NUMBER = 100;

  /**
   * Tests {@link LruQueue#addRecent(Object)} method providing a null object as argument (after
   * setting up a sample queue) and verifying if the correct exception is raised
   */
  @Test
  void testPushNull() {
    LruQueue<Object> methodLruQueue = this.createSampleQueue();
    try {
      methodLruQueue.addRecent(null);
      fail("Expected Exception Error Not Thrown!");
    } catch (NullPointerException anException) {
      assertNotNull(anException);
    }

    try {
      methodLruQueue.addLeastRecent(null);
      fail("Expected Exception Error Not Thrown!");
    } catch (NullPointerException anException) {
      assertNotNull(anException);
    }
  }

  /**
   * Tests {@link LruQueue#addRecent(Object)} method and verifies the behaviour when pushing the
   * same object more than one time.
   */
  @Test
  void testPushSameObjTwice() {
    LruQueue<Object> methodLruQueue = this.createSampleQueue();
    Object[] sampleObj = {new Object(), new Object()};

    methodLruQueue.addRecent(sampleObj[0]);
    methodLruQueue.addRecent(sampleObj[1]);

    // check size
    assertEquals(SAMPLE_ITEMS_NUMBER + 2, methodLruQueue.size());
    // check order
    assertTrue(verifyLastElemsOrder(methodLruQueue, sampleObj[0], sampleObj[1]));

    methodLruQueue.addRecent(sampleObj[0]);
    // check size
    assertEquals(SAMPLE_ITEMS_NUMBER + 2, methodLruQueue.size());
    // check order
    assertTrue(verifyLastElemsOrder(methodLruQueue, sampleObj[1], sampleObj[0]));
  }

  /** Tests {@link LruQueue#addLeastRecent(Object)} method */
  @Test
  void testPushLeast() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObj = {new Object(), new Object()};

    methodLruQueue.addRecent(sampleObj[0]);
    methodLruQueue.addLeastRecent(sampleObj[1]);

    assertEquals(2, methodLruQueue.size());
    assertTrue(verifyLastElemsOrder(methodLruQueue, sampleObj[1], sampleObj[0]));

    // --> Same element
    methodLruQueue.addLeastRecent(sampleObj[0]);

    assertEquals(2, methodLruQueue.size());
    assertTrue(verifyLastElemsOrder(methodLruQueue, sampleObj[0], sampleObj[1]));
  }

  /**
   * Tests{@link LruQueue#takeLeastRecent()} method pushing and popping objects and verifying if
   * they are correctly (in a FIFO manner) fetched and deleted
   */
  @Test
  void testPop() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();
    // pushing objects
    for (Object object : sampleObjects) methodLruQueue.addRecent(object);
    // getting objects
    for (Object sampleObject : sampleObjects)
      assertEquals(sampleObject, methodLruQueue.takeLeastRecent());
    // the queue must be empty
    assertNull(methodLruQueue.takeLeastRecent());
  }

  /**
   * Tests {@link LruQueue#size()} method checking size when empty, when putting each object and
   * when popping each object.
   */
  @Test
  void testSize() {
    Object[] sampleObjects = createSampleObjects();
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    assertEquals(0, methodLruQueue.size());
    // pushing objects
    for (int i = 0; i < sampleObjects.length; i++) {
      methodLruQueue.addRecent(sampleObjects[i]);
      assertEquals(i + 1, methodLruQueue.size());
    }
    // getting all objects
    for (int i = sampleObjects.length - 1; i >= 0; i--) {
      methodLruQueue.takeLeastRecent();
      assertEquals(i, methodLruQueue.size());
    }
    assertEquals(0, methodLruQueue.size());
  }

  /**
   * Tests {@link LruQueue#remove(Object)} method verifies if all objects are correctly removed
   * checking the method return value, if the object is still contained and the queue size.
   */
  @Test
  void testRemove() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();
    for (Object sampleObject : sampleObjects) methodLruQueue.addRecent(sampleObject);
    // removing all objects in the opposite way used by pop() method
    for (int i = sampleObjects.length - 1; i >= 0; i--) {
      assertTrue(methodLruQueue.remove(sampleObjects[i]));
      assertFalse(methodLruQueue.contains(sampleObjects[i]));
      assertEquals(i, methodLruQueue.size());
    }
  }

  /**
   * Tests{@link LruQueue#remove(Object)} providing a null argument and trying to remove it after
   * setting up a sample queue.
   */
  @Test
  void testRemoveNull() {
    LruQueue<Object> methodLruQueue = createSampleQueue();
    try {
      methodLruQueue.remove(null);
      fail("Expected Exception Error Not Thrown!");
    } catch (NullPointerException anException) {
      assertNotNull(anException);
    }
  }

  /**
   * Tests {@link LruQueue#remove(Object)} method trying to remove a not present object after
   * setting up a sample queue.
   */
  @Test
  void testRemoveNotPresent() {
    LruQueue<Object> methodLruQueue = createSampleQueue();
    assertFalse(methodLruQueue.remove(new Object()));
  }

  /**
   * Tests {@link LruQueue#contains(Object)} method trying to find a not present object after
   * setting up a sample queue. Then it search a present object.
   */
  @Test
  void testContains() {
    LruQueue<Object> methodLruQueue = createSampleQueue();
    assertFalse(methodLruQueue.contains(new Object()));
    Object methodSampleObj = new Object();
    methodLruQueue.addRecent(methodSampleObj);
    assertTrue(methodLruQueue.contains(methodSampleObj));
  }

  /** Tests {@link LruQueue#elements()} method verifying if the Enumeration provided is correct */
  @Test
  void testElements() {
    Object[] sampleObjects = createSampleObjects();
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    // pushing objects
    for (Object sampleObject : sampleObjects) methodLruQueue.addRecent(sampleObject);
    var methodIter = methodLruQueue.elements();
    int j = 0;
    while (methodIter.hasNext()) {
      assertEquals(sampleObjects[j], methodIter.next());
      j++;
    }
  }

  /**
   * Tests {@link LruQueue#toArray()} method verifying if the array generated has the same object
   * that are put into the created LRUQueue
   */
  @Test
  void testToArray() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();

    // pushing objects
    for (Object object : sampleObjects) methodLruQueue.addRecent(object);

    Object[] resultingArray = methodLruQueue.toArray();

    assertEquals(sampleObjects.length, resultingArray.length);
    for (Object sampleObject : sampleObjects) assertTrue(isPresent(resultingArray, sampleObject));
  }

  /** Tests {@link LruQueue#toArray(Object[])} method */
  @Test
  void testToArray2() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();

    // pushing objects
    for (Object object : sampleObjects) methodLruQueue.addRecent(object);

    Object[] resultingArray = new Object[sampleObjects.length];
    methodLruQueue.toArray(resultingArray);

    assertEquals(sampleObjects.length, resultingArray.length);
    for (Object sampleObject : sampleObjects) assertTrue(isPresent(resultingArray, sampleObject));
  }

  /** Tests {@link LruQueue#toArrayOrdered()} method */
  @Test
  void testToArrayOrdered() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();

    // pushing objects
    for (Object sampleObject : sampleObjects) methodLruQueue.addRecent(sampleObject);

    Object[] resultingArray = methodLruQueue.toArrayOrdered();

    assertEquals(sampleObjects.length, resultingArray.length);
    for (int i = 0; i < sampleObjects.length; i++)
      assertEquals(sampleObjects[i], resultingArray[i]);
  }

  /** Tests <code>toArrayOrdered(Object[])</code> method */
  @Test
  void testToArrayOrdered2() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();

    // pushing objects
    for (Object sampleObject : sampleObjects) methodLruQueue.addRecent(sampleObject);

    Object[] resultingArray = new Object[sampleObjects.length];
    methodLruQueue.toArrayOrdered(resultingArray);

    assertEquals(resultingArray.length, sampleObjects.length);
    for (int i = 0; i < sampleObjects.length; i++)
      assertEquals(sampleObjects[i], resultingArray[i]);
  }

  /** Tests toArray() method when the queue is empty */
  @Test
  void testToArrayEmptyQueue() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    assertEquals(0, methodLruQueue.toArray().length);
  }

  /** Tests isEmpty() method trying it with an empty queue and then with a sample queue. */
  @Test
  void testIsEmpty() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    assertTrue(methodLruQueue.isEmpty());
    methodLruQueue = createSampleQueue();
    assertFalse(methodLruQueue.isEmpty());
    // emptying the queue...
    for (int i = 0; i < SAMPLE_ITEMS_NUMBER; i++) methodLruQueue.takeLeastRecent();
    assertTrue(methodLruQueue.isEmpty());
  }

  /**
   * Creates an array of objects with a specified size
   *
   * @return the objects array
   */
  private Object[] createSampleObjects() {
    Object[] sampleObjects = new Object[LRUQueueTest.SAMPLE_ITEMS_NUMBER];
    for (int i = 0; i < sampleObjects.length; i++) sampleObjects[i] = new Object();
    return sampleObjects;
  }

  /**
   * Creates a LRUQueue filled with the specified objects number
   *
   * @return the created LRUQueue
   */
  private LruQueue<Object> createSampleQueue() {
    LruQueue<Object> methodLruQueue = new LruQueue<>();
    Object[] sampleObjects = createSampleObjects();
    for (Object sampleObject : sampleObjects) methodLruQueue.addRecent(sampleObject);
    return methodLruQueue;
  }

  /**
   * Verifies if an element is present in an array
   *
   * @param anArray the array to search into
   * @param aElementToSearch the object that must be found
   * @return true if there is at least one reference to the object
   */
  private boolean isPresent(Object[] anArray, Object aElementToSearch) {
    for (Object o : anArray) if (o.equals(aElementToSearch)) return true;
    return false;
  }

  /**
   * Verifies if the order of the last two elements in the queue is correct
   *
   * @param aLruQueue the LRUQueue to check
   * @param nextToLast the next-to-last element expected
   * @param last the last element expected
   * @return true if the order is correct
   */
  private boolean verifyLastElemsOrder(LruQueue<Object> aLruQueue, Object nextToLast, Object last) {
    boolean retVal = true;
    int size = aLruQueue.size();
    var methodIter = aLruQueue.elements();
    int counter = 0;
    while (methodIter.hasNext()) {
      // next-to-last object
      if (counter == size - 2) retVal &= methodIter.next().equals(nextToLast);
      // last object
      else if (counter == size - 1) retVal &= methodIter.next().equals(last);
      else methodIter.next();
      counter++;
    }
    return retVal;
  }
}
