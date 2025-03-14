/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

public abstract class BucketTestBase {
  protected static final byte[] DATA_LONG;
  protected static final byte[] DATA_1 =
      new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
  protected static final byte[] DATA_2 =
      new byte[] {
        0x70,
        (byte) 0x81,
        (byte) 0x92,
        (byte) 0xa3,
        (byte) 0xb4,
        (byte) 0xc5,
        (byte) 0xd6,
        (byte) 0xe7,
        (byte) 0xf8
      };

  static {
    DATA_LONG = new byte[32768 + 1]; // 32K + 1
    for (int i = 0; i < DATA_LONG.length; i++) DATA_LONG[i] = (byte) i;
  }

  @Test
  public void testReadEmpty() throws IOException {
    Bucket bucket = makeBucket(3);
    try {
      assertEquals(0, bucket.size(), "Size-0");
      OutputStream os = bucket.getOutputStream();
      os.close();

      // Read byte[]
      InputStream is = bucket.getInputStream();
      byte[] data = new byte[10];
      int read = is.read(data, 0, 10);
      is.close();

      assertEquals(-1, read, "Read-Empty");
    } finally {
      freeBucket(bucket);
    }
  }

  @Test
  public void testReadExcess() throws IOException {
    Bucket bucket = makeBucket(DATA_1.length);
    try {
      assertEquals(0, bucket.size(), "Size-0");

      // Write
      OutputStream os = bucket.getOutputStream();
      os.write(new byte[] {5});
      os.close();

      assertEquals(1, bucket.size(), "Read-Excess-Size");

      // Read byte[]
      InputStream is = bucket.getInputStream();
      byte[] data = new byte[10];
      int read = is.read(data, 0, 10);
      assertEquals(1, read, "Read-Excess");
      assertEquals(5, data[0], "Read-Excess-5");

      read = is.read(data, 0, 10);
      assertEquals(-1, read, "Read-Excess-EOF");

      is.close();
    } finally {
      freeBucket(bucket);
    }
  }

  @Test
  public void testReadWrite() throws IOException {
    Bucket bucket = makeBucket(DATA_1.length);
    try {
      assertEquals(0, bucket.size(), "Size-0");

      // Write
      OutputStream os = bucket.getOutputStream();
      os.write(DATA_1);
      os.close();

      assertEquals(DATA_1.length, bucket.size(), "Size-1");

      // Read byte[]
      InputStream is = bucket.getInputStream();
      byte[] data = new byte[DATA_1.length];
      int read = is.read(data, 0, DATA_1.length);
      is.close();

      assertEquals(DATA_1.length, read, "SimpleRead-1-SIZE");
      assertArrayEquals(DATA_1, data, "SimpleRead-1");

      // Read byte
      is = bucket.getInputStream();
      for (byte b : DATA_1) assertEquals(b, (byte) is.read(), "SimpleRead-2");

      // EOF
      assertEquals(-1, is.read(new byte[4]), "SimpleRead-EOF0");
      assertEquals(-1, is.read(), "SimpleRead-EOF1");
      assertEquals(-1, is.read(), "SimpleRead-EOF2");

      is.close();
    } finally {
      freeBucket(bucket);
    }
  }

  // Write twice -- should overwrite, not append
  @Test
  public void testReuse() throws IOException {
    if (!canOverwrite) return;

    Bucket bucket = makeBucket(DATA_1.length);
    try {
      // Write
      OutputStream os = bucket.getOutputStream();
      os.write(DATA_1);
      os.close();

      // Read byte[]
      InputStream is = bucket.getInputStream();
      byte[] data = new byte[DATA_1.length];
      int read = is.read(data, 0, DATA_1.length);
      is.close();

      assertEquals(DATA_1.length, read, "Read-1-SIZE");
      assertArrayEquals(DATA_1, data, "Read-1");

      // Write again
      os = bucket.getOutputStream();
      os.write(DATA_2);
      os.close();

      // Read byte[]
      is = bucket.getInputStream();
      data = new byte[DATA_2.length];
      read = is.read(data, 0, DATA_2.length);
      is.close();

      assertEquals(DATA_2.length, read, "Read-2-SIZE");
      assertArrayEquals(DATA_2, data, "Read-2");
    } finally {
      freeBucket(bucket);
    }
  }

  @Test
  public void testNegative() throws IOException {
    Bucket bucket = makeBucket(DATA_1.length);
    try {
      // Write
      OutputStream os = bucket.getOutputStream();
      os.write(0);
      os.write(-1);
      os.write(-2);
      os.write(123);
      os.close();

      // Read byte[]
      InputStream is = bucket.getInputStream();
      assertEquals(0xff & (byte) 0, is.read(), "Write-0");
      assertEquals(0xff & (byte) -1, is.read(), "Write-1");
      assertEquals(0xff & (byte) -2, is.read(), "Write-2");
      assertEquals(0xff & (byte) 123, is.read(), "Write-123");
      assertEquals(-1, is.read(), "EOF");
      is.close();
    } finally {
      freeBucket(bucket);
    }
  }

  @Test
  public void testLargeData() throws IOException {

    Bucket bucket = makeBucket(DATA_LONG.length * 16L);
    try (OutputStream os = bucket.getOutputStream()) {
      // Write
      for (int i = 0; i < 16; i++) os.write(DATA_LONG);
    }

    try (DataInputStream is = new DataInputStream(bucket.getInputStream())) {
      // Read byte[]
      for (int i = 0; i < 16; i++) {
        byte[] buf = new byte[DATA_LONG.length];
        is.readFully(buf);
        assertArrayEquals(DATA_LONG, buf, "Read-Long");
      }

      int read = is.read(new byte[1]);
      assertEquals(-1, read, "Read-Long-Size");

    } finally {
      freeBucket(bucket);
    }
  }

  protected abstract Bucket makeBucket(long size) throws IOException;

  protected abstract void freeBucket(Bucket bucket) throws IOException;

  protected boolean canOverwrite = true;
}
