package hyphanet.support.io.storage.bucket.wrapper;

import static org.junit.jupiter.api.Assertions.*;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.EncryptType;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.*;
import hyphanet.support.io.storage.rab.MockResumeContext;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.storage.rab.RabTestBase;
import hyphanet.support.io.util.FileSystem;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Arrays;
import java.util.Random;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptedBucketTest extends BucketTestBase {

  private static final MasterSecret secret = new MasterSecret();
  private static final EncryptType[] types = EncryptType.values();

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void testIrregularWrites() throws IOException {
    Random r = new Random(6032405);
    int length = 1024 * 64 + 1;
    byte[] data = new byte[length];
    RandomAccessBucket bucket = (RandomAccessBucket) makeBucket(length);
    OutputStream os = bucket.getOutputStream();
    r.nextBytes(data);
    for (int written = 0; written < length; ) {
      int toWrite = Math.min(length - written, 4095);
      os.write(data, written, toWrite);
      written += toWrite;
    }
    os.close();
    InputStream is = bucket.getInputStream();
    for (int moved = 0; moved < length; ) {
      int readBytes = Math.min(length - moved, 4095);
      byte[] buf = new byte[readBytes];
      readBytes = is.read(buf);
      assertTrue(readBytes > 0);
      assertArrayEquals(
          Arrays.copyOfRange(buf, 0, readBytes),
          Arrays.copyOfRange(data, moved, moved + readBytes));
      moved += readBytes;
    }
    is.close();
    bucket.dispose();
  }

  @Test
  void testIrregularWritesNotOverlapping() throws IOException {
    Random r = new Random(6032405);
    int length = 1024 * 64 + 1;
    byte[] data = new byte[length];
    RandomAccessBucket bucket = (RandomAccessBucket) makeBucket(length);
    OutputStream os = bucket.getOutputStream();
    r.nextBytes(data);
    for (int written = 0; written < length; ) {
      int toWrite = Math.min(length - written, 4095);
      os.write(data, written, toWrite);
      written += toWrite;
    }
    os.close();
    InputStream is = bucket.getInputStream();
    for (int moved = 0; moved < length; ) {
      int readBytes = Math.min(length - moved, 4093); // Co-prime with 4095
      byte[] buf = new byte[readBytes];
      readBytes = is.read(buf);
      assertTrue(readBytes > 0);
      assertArrayEquals(
          Arrays.copyOfRange(buf, 0, readBytes),
          Arrays.copyOfRange(data, moved, moved + readBytes));
      moved += readBytes;
    }
    is.close();
    bucket.dispose();
  }

  @Test
  void testBucketToRAF() throws IOException {
    Random r = new Random(6032405);
    int length = 1024 * 64 + 1;
    byte[] data = new byte[length];
    RandomAccessBucket bucket = (RandomAccessBucket) makeBucket(length);
    OutputStream os = bucket.getOutputStream();
    r.nextBytes(data);
    for (int written = 0; written < length; ) {
      int toWrite = Math.min(length - written, 4095);
      os.write(data, written, toWrite);
      written += toWrite;
    }
    os.close();
    InputStream is = bucket.getInputStream();
    for (int moved = 0; moved < length; ) {
      int readBytes = Math.min(length - moved, 4095);
      byte[] buf = new byte[readBytes];
      readBytes = is.read(buf);
      assertTrue(readBytes > 0);
      assertArrayEquals(
          Arrays.copyOfRange(buf, 0, readBytes),
          Arrays.copyOfRange(data, moved, moved + readBytes));
      moved += readBytes;
    }
    Rab rab = bucket.toRandomAccessBuffer();
    assertEquals(length, rab.size());
    RabBucket wrapped = new RabBucket(rab);
    assertTrue(BucketTools.equalBuckets(bucket, wrapped));
    for (int i = 0; i < 100; i++) {
      int end = r.nextInt(length) + 1;
      int start = r.nextInt(end);
      RabTestBase.checkArraySectionEqualsReadData(data, rab, start, end, true);
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectories(base);
  }

  @AfterEach
  void tearDown() {
    FileSystem.removeAll(base);
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  void testSerialize() throws IOException, ResumeFailedException, ClassNotFoundException {
    Path tempFile = Files.createTempFile(base, "test-storeto", ".tmp");
    byte[] buf = new byte[4096];
    Random r = new Random(1267612);
    r.nextBytes(buf);
    RegularFileBucket fb = new RegularFileBucket(tempFile, false, false, false, true);
    EncryptedBucket erab = new EncryptedBucket(types[0], fb, secret);
    OutputStream os = erab.getOutputStream();
    os.write(buf, 0, buf.length);
    os.close();
    InputStream is = erab.getInputStream();
    byte[] tmp = new byte[buf.length];
    is.read(tmp, 0, buf.length);
    is.close();
    assertArrayEquals(buf, tmp);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(erab);
    oos.close();
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
    var context = new MockResumeContext();
    context.setPersistentMasterSecret(secret);
    ObjectInputStream ois = new ObjectInputStream(dis);
    EncryptedBucket restored = (EncryptedBucket) ois.readObject();
    restored.onResume(context);
    assertEquals(buf.length, restored.size());
    assertEquals(erab, restored);
    tmp = new byte[buf.length];
    is = erab.getInputStream();
    is.read(tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    is.close();
    restored.dispose();
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  void testStoreTo() throws IOException, StorageFormatException, ResumeFailedException {
    Path tempFile = Files.createTempFile(base, "test-storeto", ".tmp");
    byte[] buf = new byte[4096];
    Random r = new Random(1267612);
    r.nextBytes(buf);
    RegularFileBucket fb = new RegularFileBucket(tempFile, false, false, false, true);
    EncryptedBucket erab = new EncryptedBucket(types[0], fb, secret);
    OutputStream os = erab.getOutputStream();
    os.write(buf, 0, buf.length);
    os.close();
    InputStream is = erab.getInputStream();
    byte[] tmp = new byte[buf.length];
    is.read(tmp, 0, buf.length);
    is.close();
    assertArrayEquals(buf, tmp);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    erab.storeTo(dos);
    dos.close();
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
    var context = new MockResumeContext();
    context.setPersistentMasterSecret(secret);
    EncryptedBucket restored =
        (EncryptedBucket)
            BucketTools.restoreFrom(
                dis, context.getPersistentFg(), context.getPersistentFileTracker(), secret);
    assertEquals(buf.length, restored.size());
    assertEquals(erab, restored);
    tmp = new byte[buf.length];
    is = erab.getInputStream();
    is.read(tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    is.close();
    restored.dispose();
  }

  @Override
  protected Bucket makeBucket(long size) {
    ArrayBucket underlying = new ArrayBucket();
    return new EncryptedBucket(types[0], underlying, secret);
  }

  @Override
  protected void freeBucket(Bucket bucket) {
    bucket.dispose();
  }

  private final Path base = Path.of("tmp.encrypted-random-access-thing-test");
}
