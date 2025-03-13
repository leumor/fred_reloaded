/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeaderStreamsTest {

  public static final String STR_HEADER = "TEST";
  public static final String STR_STRING = "testing testing 1 2 3";

  public static final byte[] bHeader = STR_HEADER.getBytes();
  public static final byte[] bString = STR_STRING.getBytes();
  public static final byte[] bJoined = (STR_HEADER + STR_STRING).getBytes();

  @BeforeEach
  void setUp() throws Exception {
    InputStream testStream = new ByteArrayInputStream(bString);
    augStream = HeaderStreams.augInput(bHeader, testStream);
    origStream = new ByteArrayOutputStream();
    dimStream = HeaderStreams.dimOutput(bHeader, origStream);
  }

  @Test
  void testAugInputRead1() throws IOException {
    int size = augStream.available();
    assertEquals(size, bHeader.length + bString.length);
    byte[] buffer = new byte[size];
    for (int i = 0; i < bJoined.length; i++) {
      int data = augStream.read();
      assertEquals(size - i - 1, augStream.available());
      assertEquals((char) data, bJoined[i]);
      buffer[i] = (byte) data;
    }
    assertArrayEquals(bJoined, buffer);
  }

  @Test
  void testAugInputReadM() throws IOException {
    _testAugInputRead(-bHeader.length);
  }

  @Test
  void testAugInputReadI() throws IOException {
    _testAugInputRead(-1);
  }

  @Test
  void testAugInputRead0() throws IOException {
    _testAugInputRead(0);
  }

  @Test
  void testAugInputReadP() throws IOException {
    _testAugInputRead(1);
  }

  @Test
  void testAugInputReadZ() throws IOException {
    _testAugInputRead(bString.length);
  }

  void _testAugInputRead(int m) throws IOException {
    int i = bHeader.length + m;
    int size = augStream.available();
    byte[] buffer = new byte[size];
    var bytesRead = augStream.read(buffer, 0, i);
    assertEquals(bytesRead, i);
    assertArrayEquals(Arrays.copyOfRange(Arrays.copyOfRange(bJoined, 0, i), 0, size), buffer);
    bytesRead = augStream.read(buffer, i, size - i);
    assertEquals(bytesRead, size - i);
    assertArrayEquals(bJoined, buffer);
  }

  @Test
  void testAugInputSkipAndReadM() throws IOException {
    _testAugInputSkipAndRead(-bHeader.length);
  }

  @Test
  void testAugInputSkipAndReadI() throws IOException {
    _testAugInputSkipAndRead(-1);
  }

  @Test
  void testAugInputSkipAndRead0() throws IOException {
    _testAugInputSkipAndRead(0);
  }

  @Test
  void testAugInputSkipAndReadP() throws IOException {
    _testAugInputSkipAndRead(1);
  }

  @Test
  void testAugInputSkipAndReadZ() throws IOException {
    _testAugInputSkipAndRead(bString.length);
  }

  void _testAugInputSkipAndRead(int m) throws IOException {
    int i = bHeader.length + m;
    var bytesSkipped = augStream.skip(i);
    assertEquals(bytesSkipped, i);
    int size = augStream.available();
    assertEquals(size, bJoined.length - i);
    byte[] buffer = new byte[size];
    int read = augStream.read(buffer);
    assertEquals(read, size);
    assertArrayEquals(Arrays.copyOfRange(bJoined, i, bJoined.length), buffer);
  }

  @Test
  void testDimOutputWrite1() throws IOException {
    for (int i = 0; i < bJoined.length; i++) {
      assertArrayEquals(
          origStream.toByteArray(),
          (i < bHeader.length) ? new byte[0] : Arrays.copyOfRange(bString, 0, i - bHeader.length));
      dimStream.write(bJoined[i]);
    }
    assertArrayEquals(bString, origStream.toByteArray());
  }

  @Test
  void testDimOutputWriteM() throws IOException {
    _testDimOutputWrite(-bHeader.length);
  }

  @Test
  void testDimOutputWriteI() throws IOException {
    _testDimOutputWrite(-1);
  }

  @Test
  void testDimOutputWrite0() throws IOException {
    _testDimOutputWrite(0);
  }

  @Test
  void testDimOutputWriteP() throws IOException {
    _testDimOutputWrite(1);
  }

  @Test
  void testDimOutputWriteZ() throws IOException {
    _testDimOutputWrite(bString.length);
  }

  void _testDimOutputWrite(int m) throws IOException {
    int i = bHeader.length + m;
    dimStream.write(Arrays.copyOfRange(bJoined, 0, i));
    assertArrayEquals(
        origStream.toByteArray(),
        (i < bHeader.length) ? new byte[0] : Arrays.copyOfRange(bString, 0, i - bHeader.length));
    dimStream.write(Arrays.copyOfRange(bJoined, i, bJoined.length));
    assertArrayEquals(bString, origStream.toByteArray());
  }

  @Test
  void testDimOutputThrow0() {
    assertArrayEquals(new byte[0], origStream.toByteArray());
    assertThrows(IOException.class, () -> dimStream.write('!'));
  }

  @Test
  void testDimOutputThrow1() throws IOException {
    dimStream.write('T');
    assertArrayEquals(new byte[0], origStream.toByteArray());
    assertThrows(IOException.class, () -> dimStream.write("!!!".getBytes()));
  }

  InputStream augStream;
  ByteArrayOutputStream origStream;
  OutputStream dimStream;
}
