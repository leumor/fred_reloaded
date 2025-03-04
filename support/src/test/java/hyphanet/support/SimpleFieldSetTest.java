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

package hyphanet.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.junit.jupiter.api.Assertions.*;

import hyphanet.base.Base64;
import hyphanet.support.io.LineReader;
import hyphanet.support.io.ReaderUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link SimpleFieldSet} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
class SimpleFieldSetTest {

  private static final char KEY_VALUE_SEPARATOR = '=';

  /* A double string array used across all tests
   * it must not be changed in order to perform tests
   * correctly */
  private static final String[][] SAMPLE_STRING_PAIRS = {
    // directSubset
    {"foo", "bar"},
    {"foo.bar", "foobar"},
    {"foo.bar.foo", "foobar"},
    {"foo.bar.boo.far", "foobar"},
    {"foo2", "foobar.fooboo.foofar.foofoo"},
    {"foo3", KEY_VALUE_SEPARATOR + "bar"}
  };

  private static final String SAMPLE_END_MARKER = "END";

  /** Tests put() and get() methods using a normal Map behaviour and without MULTI_LEVEL_CHARs */
  @Test
  void testSimpleFieldSetPutAndGet_NoMultiLevel() {
    String[][] methodPairsArray = {
      {"A", "a"}, {"B", "b"}, {"C", "c"}, {"D", "d"}, {"E", "e"}, {"F", "f"}
    };
    assertTrue(checkPutAndGetPairs(methodPairsArray));
  }

  /** Tests put() and get() methods using a normal Map behaviour and with MULTI_LEVEL_CHARs */
  @Test
  void testSimpleFieldSetPutAndGet_MultiLevel() {
    String[][] methodPairsArrayDoubleLevel = {
      {"A.A", "aa"}, {"A.B", "ab"}, {"A.C", "ac"}, {"A.D", "ad"}, {"A.E", "ae"}, {"A.F", "af"}
    };
    String[][] methodPairsArrayMultiLevel = {
      {"A.A.A.A", "aa"},
      {"A.B.A", "ab"},
      {"A.C.Cc", "ac"},
      {"A.D.F", "ad"},
      {"A.E.G", "ae"},
      {"A.F.J.II.UI.BOO", "af"}
    };
    assertTrue(checkPutAndGetPairs(methodPairsArrayDoubleLevel));
    assertTrue(checkPutAndGetPairs(methodPairsArrayMultiLevel));
  }

  /**
   * Tests subset(String) method putting two levels keys and fetching it through subset() method on
   * the first level and then get() on the second
   */
  @Test
  void testSimpleFieldSetSubset_String() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    String[][] methodPairsArrayMultiLevel = {
      {"A", "A", "aa"},
      {"A", "B", "ab"},
      {"A", "C", "ac"},
      {"A", "D", "ad"},
      {"A", "E", "ae"},
      {"A", "F", "af"}
    };
    // putting values
    for (String[] strings : methodPairsArrayMultiLevel) {
      methodSFS.putSingle(strings[0] + SimpleFieldSet.MULTI_LEVEL_CHAR + strings[1], strings[2]);
    }
    // getting subsets and then values
    for (String[] strings : methodPairsArrayMultiLevel) {
      assertEquals(methodSFS.subset(strings[0]).get(strings[1]), strings[2]);
    }
    assertTrue(checkSimpleFieldSetSize(methodSFS, methodPairsArrayMultiLevel.length));
  }

  /**
   * Tests putAllOverwrite(SimpleFieldSet) method trying to overwrite a whole SimpleFieldSet with
   * another with same keys but different values
   */
  @Test
  void testPutAllOverwrite() {
    String methodAppendedString = "buu";
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    SimpleFieldSet methodNewSFS = this.sfsFromStringPairs(methodAppendedString);
    methodSFS.putAllOverwrite(methodNewSFS);
    for (String[] sampleStringPair : SAMPLE_STRING_PAIRS) {
      assertEquals(methodSFS.get(sampleStringPair[0]), sampleStringPair[1] + methodAppendedString);
    }
    SimpleFieldSet nullSFS = new SimpleFieldSet(false);
    nullSFS.putAllOverwrite(methodNewSFS);
    for (String[] sampleStringPair : SAMPLE_STRING_PAIRS) {
      assertEquals(nullSFS.get(sampleStringPair[0]), sampleStringPair[1] + methodAppendedString);
    }
  }

  /** Tests put(String,SimpleFieldSet) method */
  @Test
  void testPut_StringSimpleFieldSet() {
    String methodKey = "prefix";
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    methodSFS.put(methodKey, sfsFromSampleStringPairs());
    for (String[] sampleStringPair : SAMPLE_STRING_PAIRS) {
      assertEquals(
          methodSFS.get(methodKey + SimpleFieldSet.MULTI_LEVEL_CHAR + sampleStringPair[0]),
          sampleStringPair[1]);
    }
  }

  /** Tests put(String,SimpleFieldSet) method */
  @Test
  void testTPut_StringSimpleFieldSet() {
    String methodKey = "prefix";
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    methodSFS.tput(methodKey, sfsFromSampleStringPairs());
    for (String[] sampleStringPair : SAMPLE_STRING_PAIRS) {
      assertEquals(
          methodSFS.get(methodKey + SimpleFieldSet.MULTI_LEVEL_CHAR + sampleStringPair[0]),
          sampleStringPair[1]);
    }
  }

  /**
   * Tests put(String,SimpleFieldSet) and tput(String,SimpleFieldSet) trying to add empty data
   * structures
   */
  @Test
  void testPutAndTPut_WithEmpty() {
    SimpleFieldSet methodEmptySFS = new SimpleFieldSet(true);
    SimpleFieldSet methodSampleSFS = sfsFromSampleStringPairs();
    try {
      methodSampleSFS.put("sample", methodEmptySFS);
      fail("Expected Exception Error Not Thrown!");
    } catch (IllegalArgumentException anException) {
      assertNotNull(anException);
    }
    try {
      methodSampleSFS.tput("sample", methodSampleSFS);
    } catch (IllegalArgumentException aException) {
      fail("Not expected exception thrown : " + aException.getMessage());
    }
  }

  /**
   * Tests put(String,boolean) and getBoolean(String,boolean) methods consistency. The default value
   * (returned if the key is not found) is set to "false" and the real value is always set to
   * "true", so we are sure if it finds the right value or not (and does not use the default).
   */
  @Test
  void testPut_StringBoolean() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    int length = 15;
    for (int i = 0; i < length; i++) {
      methodSFS.put(Integer.toString(i), true);
    }
    for (int i = 0; i < length; i++) {
      assertTrue(methodSFS.getBoolean(Integer.toString(i), false));
    }
    assertTrue(checkSimpleFieldSetSize(methodSFS, length));
  }

  /**
   * Tests put(String,int) and [getInt(String),getInt(String,int)] methods consistency. The default
   * value (returned if the key is not found) is set to a not present int value, so we are sure if
   * it finds the right value or not (and does not use the default).
   */
  @Test
  void testPut_StringInt() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    int[][] methodPairsArray = {{1, 1}, {2, 2}, {3, 3}, {4, 4}};
    for (int[] ints : methodPairsArray) {
      methodSFS.put(Integer.toString(ints[0]), ints[1]);
    }

    assertTrue(checkSimpleFieldSetSize(methodSFS, methodPairsArray.length));

    for (int[] ints : methodPairsArray) {
      try {
        assertEquals(methodSFS.getInt(Integer.toString(ints[0])), ints[1]);
        assertEquals(methodSFS.getInt(Integer.toString(ints[0]), 5), ints[1]);
      } catch (SimpleFieldSet.FSParseException aException) {
        fail("Not expected exception thrown : " + aException.getMessage());
      }
    }
  }

  /**
   * Tests put(String,long) and [getLong(String),getLong(String,long)] methods consistency. The
   * default value (returned if the key is not found) is set to a not present long value, so we are
   * sure if it finds the right value or not (and does not use the default).
   */
  @Test
  void testPut_StringLong() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    long[][] methodPairsArray = {{1, 1}, {2, 2}, {3, 3}, {4, 4}};
    for (long[] value : methodPairsArray) {
      methodSFS.put(Long.toString(value[0]), value[1]);
    }

    assertTrue(checkSimpleFieldSetSize(methodSFS, methodPairsArray.length));

    for (long[] longs : methodPairsArray) {
      try {
        assertEquals(methodSFS.getLong(Long.toString(longs[0])), longs[1]);
        assertEquals(methodSFS.getLong(Long.toString(longs[0]), 5), longs[1]);
      } catch (SimpleFieldSet.FSParseException aException) {
        fail("Not expected exception thrown : " + aException.getMessage());
      }
    }
  }

  /**
   * Tests put(String,char) and [getChar(String),getChar(String,char)] methods consistency. The
   * default value (returned if the key is not found) is set to a not present char value, so we are
   * sure if it finds the right value or not (and does not use the default).
   */
  @Test
  void testPut_StringChar() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    char[][] methodPairsArray = {{'1', '1'}, {'2', '2'}, {'3', '3'}, {'4', '4'}};
    for (char[] value : methodPairsArray) {
      methodSFS.put(String.valueOf(value[0]), value[1]);
    }

    assertTrue(checkSimpleFieldSetSize(methodSFS, methodPairsArray.length));

    for (char[] chars : methodPairsArray) {
      try {
        assertEquals(methodSFS.getChar(String.valueOf(chars[0])), chars[1]);
        assertEquals(methodSFS.getChar(String.valueOf(chars[0]), '5'), chars[1]);
      } catch (SimpleFieldSet.FSParseException aException) {
        fail("Not expected exception thrown : " + aException.getMessage());
      }
    }
  }

  /**
   * Tests put(String,short) and [getShort(String)|getShort(String,short)] methods consistency. The
   * default value (returned if the key is not found) is set to a not present short value, so we are
   * sure if it finds the right value or not (and does not use the default).
   */
  @Test
  void testPut_StringShort() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    short[][] methodPairsArray = {{1, 1}, {2, 2}, {3, 3}, {4, 4}};
    for (short[] value : methodPairsArray) {
      methodSFS.put(Short.toString(value[0]), value[1]);
    }

    assertTrue(checkSimpleFieldSetSize(methodSFS, methodPairsArray.length));

    for (short[] shorts : methodPairsArray) {
      try {
        assertEquals(methodSFS.getShort(Short.toString(shorts[0])), shorts[1]);
        assertEquals(methodSFS.getShort(Short.toString(shorts[0]), (short) 5), shorts[1]);
      } catch (SimpleFieldSet.FSParseException aException) {
        fail("Not expected exception thrown : " + aException.getMessage());
      }
    }
  }

  /**
   * Tests put(String,double) and [getDouble(String)|getDouble(String,double)] methods consistency.
   * The default value (returned if the key is not found) is set to a not present double value, so
   * we are sure if it finds the right value or not (and does not use the default).
   */
  @Test
  void testPut_StringDouble() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(false);
    double[][] methodPairsArray = {{1, 1}, {2, 2}, {3, 3}, {4, 4}};
    for (double[] value : methodPairsArray) {
      methodSFS.put(Double.toString(value[0]), value[1]);
    }

    assertTrue(checkSimpleFieldSetSize(methodSFS, methodPairsArray.length));

    for (double[] doubles : methodPairsArray) {
      try {
        // there is no assertEquals(Double,Double) so we are obliged to do this way -_-
        assertEquals(
            0, Double.compare((methodSFS.getDouble(Double.toString(doubles[0]))), doubles[1]));
        assertEquals(
            0, Double.compare(methodSFS.getDouble(Double.toString(doubles[0]), 5), doubles[1]));
      } catch (SimpleFieldSet.FSParseException aException) {
        fail("Not expected exception thrown : " + aException.getMessage());
      }
    }
  }

  /**
   * Tests SimpleFieldSet(String,boolean,boolean) constructor, with simple and border cases of the
   * canonical form.
   */
  @Test
  void testSimpleFieldSet_StringBooleanBoolean() {
    String[][] methodStringPairs = SAMPLE_STRING_PAIRS;
    String methodStringToParse = sfsReadyString(methodStringPairs);
    try {
      SimpleFieldSet methodSFS = new SimpleFieldSet(methodStringToParse, false, false);
      for (String[] methodStringPair : methodStringPairs) {
        assertEquals(methodSFS.get(methodStringPair[0]), methodStringPair[1]);
      }
    } catch (IOException aException) {
      fail("Not expected exception thrown : " + aException.getMessage());
    }
  }

  /**
   * Tests SimpleFieldSet(BufferedReader,boolean,boolean) constructor, with simple and border cases
   * of the canonical form.
   */
  @Test
  void testSimpleFieldSet_BufferedReaderBooleanBoolean() {
    String[][] methodStringPairs = SAMPLE_STRING_PAIRS;
    BufferedReader methodBufferedReader =
        new BufferedReader(new StringReader(sfsReadyString(methodStringPairs)));
    try {
      SimpleFieldSet methodSFS = new SimpleFieldSet(methodBufferedReader, false);
      for (String[] methodStringPair : methodStringPairs) {
        assertEquals(methodSFS.get(methodStringPair[0]), methodStringPair[1]);
      }
    } catch (IOException aException) {
      fail("Not expected exception thrown : " + aException.getMessage());
    }
  }

  /**
   * Tests SimpleFieldSet(SimpleFieldSet) constructor, with simple and border cases of the canonical
   * form.
   */
  @Test
  void testSimpleFieldSet_SimpleFieldSet() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(sfsFromSampleStringPairs());
    for (String[] methodStringPair : SAMPLE_STRING_PAIRS) {
      assertEquals(methodSFS.get(methodStringPair[0]), methodStringPair[1]);
    }
  }

  /** Tests {get,set}EndMarker(String) methods using them after a String parsing */
  @Test
  void testEndMarker() {
    String methodEndMarker = "ANOTHER-ENDING";
    String methodStringToParse = sfsReadyString(SAMPLE_STRING_PAIRS);
    try {
      SimpleFieldSet methodSFS = new SimpleFieldSet(methodStringToParse, false, false);
      assertEquals(SAMPLE_END_MARKER, methodSFS.getEndMarker());
      methodSFS.setEndMarker(methodEndMarker);
      assertEquals(methodEndMarker, methodSFS.getEndMarker());
    } catch (IOException aException) {
      fail("Not expected exception thrown : " + aException.getMessage());
    }
  }

  /** Tests isEmpty() method. */
  @Test
  void testIsEmpty() {
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    assertFalse(methodSFS.isEmpty());
    methodSFS = new SimpleFieldSet(true);
    assertTrue(methodSFS.isEmpty());
  }

  /**
   * Tests directSubsetNameIterator() method. It uses SAMPLE_STRING_PAIRS and for this reason the
   * expected subset is "foo".
   */
  @Test
  void testDirectSubsetNameIterator() {
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    String expectedSubset = SAMPLE_STRING_PAIRS[0][0]; // "foo"
    Iterator<String> methodIter = methodSFS.directSubsetNameIterator();
    while (methodIter.hasNext()) {
      assertEquals(methodIter.next(), expectedSubset);
    }
    methodSFS = new SimpleFieldSet(true);
    methodIter = methodSFS.directSubsetNameIterator();
    assertNull(methodIter);
  }

  /** Tests nameOfDirectSubsets() method. */
  @Test
  void testNamesOfDirectSubsets() {
    String[] expectedResult = {SAMPLE_STRING_PAIRS[0][0]};
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    assertArrayEquals(methodSFS.namesOfDirectSubsets(), expectedResult);

    methodSFS = new SimpleFieldSet(true);
    assertArrayEquals(new String[0], methodSFS.namesOfDirectSubsets());
  }

  /** Test the putOverwrite(String,String) method. */
  @Test
  void testPutOverwrite_String() {
    String methodKey = "foo.bar";
    String[] methodValues = {"boo", "bar", "zoo"};
    String expectedResult = "zoo";
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    for (String methodValue : methodValues) {
      methodSFS.putOverwrite(methodKey, methodValue);
    }
    assertEquals(expectedResult, methodSFS.get(methodKey));
  }

  /** Test the putOverwrite(String,String[]) method. */
  @Test
  void testPutOverwrite_StringArray() {
    String methodKey = "foo.bar";
    String[] methodValues = {"boo", "bar", "zoo"};
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    methodSFS.putOverwrite(methodKey, methodValues);
    assertArrayEquals(methodSFS.getAll(methodKey), methodValues);
  }

  /** Test the putAppend(String,String) method. */
  @Test
  void testPutAppend() {
    String methodKey = "foo.bar";
    String[] methodValues = {"boo", "bar", "zoo"};
    String expectedResult =
        "boo" + SimpleFieldSet.MULTI_VALUE_CHAR + "bar" + SimpleFieldSet.MULTI_VALUE_CHAR + "zoo";
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    for (String methodValue : methodValues) {
      methodSFS.putAppend(methodKey, methodValue);
    }
    assertEquals(expectedResult, methodSFS.get(methodKey));
  }

  /** Tests the getAll(String) method. */
  @Test
  void testGetAll() {
    String methodKey = "foo.bar";
    String[] methodValues = {"boo", "bar", "zoo"};
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    for (String methodValue : methodValues) {
      methodSFS.putAppend(methodKey, methodValue);
    }
    assertArrayEquals(methodSFS.getAll(methodKey), methodValues);
  }

  /** Tests the getIntArray(String) method */
  @Test
  void testGetIntArray() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    String keyPrefix = "foo";
    for (int i = 0; i < 15; i++) {
      methodSFS.putAppend(keyPrefix, String.valueOf(i));
    }
    int[] result = methodSFS.getIntArray(keyPrefix);
    for (int i = 0; i < 15; i++) {
      assertEquals(result[i], i);
    }
  }

  /** Tests the getDoubleArray(String) method */
  @Test
  void testGetDoubleArray() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    String keyPrefix = "foo";
    for (int i = 0; i < 15; i++) {
      methodSFS.putAppend(keyPrefix, String.valueOf((double) i));
    }
    double[] result = methodSFS.getDoubleArray(keyPrefix);
    for (int i = 0; i < 15; i++) {
      assertEquals(result[i], (i));
    }
  }

  /** Tests removeValue(String) method */
  @Test
  void testRemoveValue() {
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    methodSFS.removeValue("foo");
    assertNull(methodSFS.get(SAMPLE_STRING_PAIRS[0][0]));
    for (int i = 1; i < SAMPLE_STRING_PAIRS.length; i++) {
      assertEquals(methodSFS.get(SAMPLE_STRING_PAIRS[i][0]), SAMPLE_STRING_PAIRS[i][1]);
    }
  }

  /** Tests removeSubset(String) method */
  @Test
  void testRemoveSubset() {
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    methodSFS.removeSubset("foo");
    for (int i = 1; i < 4; i++) {
      assertNull(methodSFS.get(SAMPLE_STRING_PAIRS[i][0]));
    }
    assertEquals(SAMPLE_STRING_PAIRS[0][1], methodSFS.get(SAMPLE_STRING_PAIRS[0][0]));
    for (int i = 4; i < 6; i++) {
      assertEquals(methodSFS.get(SAMPLE_STRING_PAIRS[i][0]), SAMPLE_STRING_PAIRS[i][1]);
    }
  }

  /**
   * Tests the Iterator given for the SimpleFieldSet class. It tests hasNext() and next() methods.
   */
  @Test
  void testKeyIterator() {
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    Iterator<String> itr = methodSFS.keyIterator();
    assertTrue(areAllContainedKeys("", itr));
  }

  /** Tests the Iterator created using prefix given for the SimpleFieldSet class */
  @Test
  void testKeyIterator_String() {
    String methodPrefix = "bob";
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    Iterator<String> itr = methodSFS.keyIterator(methodPrefix);
    assertTrue(areAllContainedKeys(methodPrefix, itr));
  }

  /**
   * Tests the toplevelIterator given for the SimpleFieldSet class. It tests hasNext() and next()
   * methods.
   *
   * <p>TODO: improve the test
   */
  @Test
  void testToplevelKeyIterator() {
    SimpleFieldSet methodSFS = sfsFromSampleStringPairs();
    Iterator<String> itr = methodSFS.toplevelKeyIterator();

    for (int i = 0; i < 3; i++) {
      assertTrue(itr.hasNext());
      assertTrue(isAKey("", itr.next()));
    }
    assertFalse(itr.hasNext());
  }

  @Test
  void testKeyIterationPastEnd() {
    System.out.println("Starting iterator test");

    SimpleFieldSet sfs = new SimpleFieldSet(true);
    sfs.putOverwrite("test", "test");

    Iterator<String> keyIterator = sfs.keyIterator();
    assertEquals("test", keyIterator.next());

    try {
      String s = keyIterator.next();
      fail("Expected NoSuchElementException, but got " + s);
    } catch (NoSuchElementException e) {
      // Expected
    }
  }

  @Test
  void testBase64() throws IOException {
    checkBase64("test", " ", "IA");
    for (String[] s : SAMPLE_STRING_PAIRS) {
      String evilValue = "=" + s[1];
      String base64 = Base64.encodeUTF8(evilValue);
      checkBase64(s[0], evilValue, base64);
    }
  }

  @Test
  void testEmptyValue() throws IOException {
    String written = "foo.blah=\nEnd\n";
    LineReader r = ReaderUtil.fromBufferedReader(new BufferedReader(new StringReader(written)));
    SimpleFieldSet sfsCheck = new SimpleFieldSet(r, 1024, 1024, true, false, false);
    assertTrue(Objects.requireNonNull(sfsCheck.get("foo.blah")).isEmpty());
    r = ReaderUtil.fromBufferedReader(new BufferedReader(new StringReader(written)));
    sfsCheck = new SimpleFieldSet(r, 1024, 1024, true, false, true);
    assertTrue(Objects.requireNonNull(sfsCheck.get("foo.blah")).isEmpty());
  }

  @Test
  void testSplit() {
    assertArrayEquals(new String[] {"blah"}, SimpleFieldSet.split("blah"));
    assertArrayEquals(new String[] {"blah", " blah"}, SimpleFieldSet.split("blah; blah"));
    assertArrayEquals(new String[] {"blah", "1", "2"}, SimpleFieldSet.split("blah;1;2"));
    assertArrayEquals(new String[] {"blah", "1", "2", ""}, SimpleFieldSet.split("blah;1;2;"));
    assertArrayEquals(new String[] {"blah", "1", "2", "", ""}, SimpleFieldSet.split("blah;1;2;;"));
    assertArrayEquals(
        new String[] {"", "blah", "1", "2", "", ""}, SimpleFieldSet.split(";blah;1;2;;"));
    assertArrayEquals(
        new String[] {"", "", "blah", "1", "2", "", ""}, SimpleFieldSet.split(";;blah;1;2;;"));
    assertArrayEquals(new String[] {"", "", ""}, SimpleFieldSet.split(";;;"));
  }

  // This fixes https://freenet.mantishub.io/view.php?id=7197.
  @Test
  void directSubsetsReturnsEmptyMapWhenSubsetsIsNotInitialized() {
    SimpleFieldSet simpleFieldSet = new SimpleFieldSet(true);
    assertThat(simpleFieldSet.directSubsets(), anEmptyMap());
  }

  /**
   * Tests putAppend(String,String) method trying to store a key with two paired multi_level_chars
   * (i.e. "..").
   */
  @Test
  void testSimpleFieldSetPutAppend_StringString_WithTwoPairedMultiLevelChars() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    String methodKey = "foo..bar";
    String methodValue = "foobar";
    methodSFS.putAppend(methodKey, methodValue);
    assertEquals(methodValue, methodSFS.get(methodKey));
  }

  /**
   * Tests putSingle(String,String) method trying to store a key with two paired multi_level_chars
   * (i.e. "..").
   */
  @Test
  void testSimpleFieldSetPutSingle_StringString_WithTwoPairedMultiLevelChars() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    String methodKey = "foo..bar.";
    String methodValue = "foobar";
    methodSFS.putSingle(methodKey, methodValue);
    assertEquals(methodValue, methodSFS.subset("foo").subset("").subset("bar").get(""));
    assertEquals(methodValue, methodSFS.get(methodKey));
  }

  /**
   * It puts key-value pairs in a SimpleFieldSet and verify if it can do the correspondant get
   * correctly.
   *
   * @return true if it is correct
   */
  private boolean checkPutAndGetPairs(String[][] aPairsArray) {
    boolean retValue = true;
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    // putting values
    for (String[] value : aPairsArray) {
      methodSFS.putSingle(value[0], value[1]);
    }
    // getting values
    for (String[] strings : aPairsArray) {
      retValue &= Objects.requireNonNull(methodSFS.get(strings[0])).equals(strings[1]);
    }
    retValue &= checkSimpleFieldSetSize(methodSFS, aPairsArray.length);
    return retValue;
  }

  /**
   * It creates an SFS from the SAMPLE_STRING_PAIRS and putting a suffix after every value
   *
   * @param aSuffix to put after every value
   * @return the SimpleFieldSet created
   */
  private SimpleFieldSet sfsFromStringPairs(String aSuffix) {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    // creating new
    for (String[] sampleStringPair : SAMPLE_STRING_PAIRS) {
      methodSFS.putSingle(sampleStringPair[0], sampleStringPair[1] + aSuffix);
    }
    return methodSFS;
  }

  /**
   * Checks if the provided SimpleFieldSet has the right size
   *
   * @return true if the size is the expected
   */
  private boolean checkSimpleFieldSetSize(SimpleFieldSet aSimpleFieldSet, int expectedSize) {
    int actualSize = 0;
    Iterator<String> methodKeyIterator = aSimpleFieldSet.keyIterator();
    while (methodKeyIterator.hasNext()) {
      methodKeyIterator.next();
      actualSize++;
    }
    return expectedSize == actualSize;
  }

  /**
   * Generates a string for the SFS parser in the canonical form: key=value END
   *
   * @return a String ready to be read by a SFS parser
   */
  private String sfsReadyString(String[][] aStringPairsArray) {

    StringBuilder methodStringToReturn = new StringBuilder();
    for (String[] strings : aStringPairsArray) {
      methodStringToReturn
          .append(strings[0])
          .append(KEY_VALUE_SEPARATOR)
          .append(strings[1])
          .append('\n');
    }
    methodStringToReturn.append(SAMPLE_END_MARKER);
    return methodStringToReturn.toString();
  }

  /**
   * Generates a SimpleFieldSet using the SAMPLE_STRING_PAIRS and sfs put method
   *
   * @return a SimpleFieldSet
   */
  private SimpleFieldSet sfsFromSampleStringPairs() {
    SimpleFieldSet methodSFS = new SimpleFieldSet(true);
    for (String[] sampleStringPair : SAMPLE_STRING_PAIRS) {
      methodSFS.putSingle(sampleStringPair[0], sampleStringPair[1]);
    }
    assertTrue(checkSimpleFieldSetSize(methodSFS, SAMPLE_STRING_PAIRS.length));
    return methodSFS;
  }

  /**
   * Searches for a key in a given String[][] array. We consider that keys are stored in
   * String[x][0]
   *
   * @param aPrefix that could be put before found key
   * @param aKey to be searched
   * @return true if there is the key
   */
  private boolean isAKey(String aPrefix, String aKey) {
    for (String[] strings : SimpleFieldSetTest.SAMPLE_STRING_PAIRS) {
      if (aKey.equals(aPrefix + strings[0])) {
        return true;
      }
    }
    return false;
  }

  /**
   * Verifies if all keys in a String[][] (We consider that keys are stored in String[x][0]) are the
   * same that the Iterator provides. In this way both hasNext() and next() methods are tested.
   *
   * @param aPrefix that could be put before found key
   * @return true if they have the same key set
   */
  private boolean areAllContainedKeys(String aPrefix, Iterator<String> aIterator) {
    boolean retValue = true;
    int actualLength = 0;
    while (aIterator.hasNext()) {
      actualLength++;
      retValue &= isAKey(aPrefix, aIterator.next());
    }
    retValue &= (actualLength == SimpleFieldSetTest.SAMPLE_STRING_PAIRS.length);
    return retValue;
  }

  private void checkBase64(String key, String value, String base64Value) throws IOException {
    SimpleFieldSet sfs = new SimpleFieldSet();
    sfs.putSingle(key, value);
    assertEquals(sfs.toOrderedString(), key + "=" + value + "\nEnd\n");
    StringWriter sw = new StringWriter();
    sfs.writeTo(sw, "", false, true);
    String written = sw.toString();
    assertEquals(written, key + "==" + base64Value + "\nEnd\n");
    LineReader r = ReaderUtil.fromBufferedReader(new BufferedReader(new StringReader(written)));
    SimpleFieldSet sfsCheck = new SimpleFieldSet(r, 1024, 1024, true, false, true);
    assertEquals(sfsCheck.get(key), value);
  }
}
