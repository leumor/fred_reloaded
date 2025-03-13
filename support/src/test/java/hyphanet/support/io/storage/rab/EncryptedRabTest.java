/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.rab;

import static org.junit.jupiter.api.Assertions.*;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.EncryptType;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.BucketTools;
import hyphanet.support.io.util.FileSystem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Random;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptedRabTest {
  private static final EncryptType[] types = EncryptType.values();
  private static final byte[] message = "message".getBytes(StandardCharsets.UTF_8);
  private static final MasterSecret secret = new MasterSecret();
  private static final long FALSE_MAGIC = 0x2c158a6c8882ffd3L;

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  void testPreadFileOffsetTooBig() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    int len = 20;
    byte[] result = new byte[len];
    int offset = 100;
    var thrown = assertThrows(IOException.class, () -> erat.pread(offset, result, 0, len));
    assertEquals(
        "Cannot read after end: trying to read from "
            + offset
            + " to "
            + (offset + len)
            + " on block length "
            + erat.size(),
        thrown.getMessage());
  }

  @Test
  void testPwriteFileOffsetTooSmall() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    byte[] result = new byte[20];
    var thrown = assertThrows(IllegalArgumentException.class, () -> erat.pwrite(-1, result, 0, 20));
    assertEquals("Cannot read before zero", thrown.getMessage());
  }

  @Test
  void testPwriteFileOffsetTooBig() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    int len = 20;
    byte[] result = new byte[len];
    int offset = 100;
    var thrown = assertThrows(IOException.class, () -> erat.pwrite(offset, result, 0, len));
    assertEquals(
        "Cannot write after end: trying to write from "
            + offset
            + " to "
            + (offset + len)
            + " on block length "
            + erat.size(),
        thrown.getMessage());
  }

  @Test
  void testClose() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    assertDoesNotThrow(
        () -> {
          erat.close();
          erat.close();
        });
  }

  @Test
  void testClosePread() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    erat.close();
    byte[] result = new byte[20];
    var thrown = assertThrows(IOException.class, () -> erat.pread(0, result, 0, 20));
    assertEquals(
        "This RandomAccessBuffer has already been closed. It can no longer be read from.",
        thrown.getMessage());
  }

  @Test
  void testClosePwrite() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    erat.close();
    byte[] result = new byte[20];
    var thrown = assertThrows(IOException.class, () -> erat.pwrite(0, result, 0, 20));
    assertEquals(
        "This RandomAccessBuffer has already been closed. It can no longer be written to.",
        thrown.getMessage());
  }

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectory(base);
  }

  @AfterEach
  void tearDown() {
    FileSystem.removeAll(base);
  }

  @Test
  void testStoreTo()
      throws IOException, StorageFormatException, ResumeFailedException, GeneralSecurityException {
    Path tempFile = Files.createTempFile(base, "test-storeto", ".tmp");
    byte[] buf = new byte[4096];
    Random r = new Random(1267612);
    r.nextBytes(buf);
    RegularFileRab rafw = new RegularFileRab(tempFile, buf.length + types[0].headerLen, false);
    EncryptedRab eraf = new EncryptedRab(types[0], rafw, secret, true);
    eraf.pwrite(0, buf, 0, buf.length);
    byte[] tmp = new byte[buf.length];
    eraf.pread(0, tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    eraf.storeTo(dos);
    dos.close();
    eraf.close();
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
    var context = new MockResumeContext();
    context.setPersistentMasterSecret(secret);
    EncryptedRab restored =
        (EncryptedRab)
            BucketTools.restoreRabFrom(
                dis, context.getPersistentFg(), context.getPersistentFileTracker(), secret);
    assertEquals(buf.length, restored.size());
    // assertEquals(rafw, restored);
    tmp = new byte[buf.length];
    restored.pread(0, tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    restored.close();
    restored.dispose();
  }

  @Test
  void testSerialize()
      throws IOException, ResumeFailedException, GeneralSecurityException, ClassNotFoundException {
    Path tempFilePath = Files.createTempFile(base, "test-storeto", ".tmp");
    byte[] buf = new byte[4096];
    Random r = new Random(1267612);
    r.nextBytes(buf);
    var rafw = new RegularFileRab(tempFilePath, buf.length + types[0].headerLen, false);
    EncryptedRab eraf = new EncryptedRab(types[0], rafw, secret, true);
    eraf.pwrite(0, buf, 0, buf.length);
    byte[] tmp = new byte[buf.length];
    eraf.pread(0, tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(eraf);
    oos.close();
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
    var context = new MockResumeContext();
    context.setPersistentMasterSecret(secret);
    ObjectInputStream ois = new ObjectInputStream(dis);
    EncryptedRab restored = (EncryptedRab) ois.readObject();
    restored.onResume(context);
    assertEquals(buf.length, restored.size());
    assertEquals(eraf, restored);
    tmp = new byte[buf.length];
    restored.pread(0, tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    restored.close();
    restored.dispose();
  }

  @Test
  void testSize() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    assertEquals(erat.size(), barat.size() - types[0].headerLen);
  }

  @Test
  void testPreadFileOffsetTooSmall() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    byte[] result = new byte[20];
    var thrown = assertThrows(IllegalArgumentException.class, () -> erat.pread(-1, result, 0, 20));
    assertEquals("Cannot read before zero", thrown.getMessage());
  }

  @Test
  void testSuccessfulRoundTripReadHeader() throws IOException, GeneralSecurityException {
    for (EncryptType type : types) {
      byte[] bytes = new byte[100];
      ByteArrayRab barat = new ByteArrayRab(bytes);
      EncryptedRab erat = new EncryptedRab(type, barat, secret, true);
      erat.pwrite(0, message, 0, message.length);
      erat.close();
      ByteArrayRab barat2 = new ByteArrayRab(barat.getBuffer());
      EncryptedRab erat2 = new EncryptedRab(type, barat2, secret, false);
      byte[] result = new byte[message.length];
      erat2.pread(0, result, 0, result.length);
      erat2.close();
      assertArrayEquals(message, result);
    }
  }

  @Test
  void testWrongERATType() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    erat.close();
    ByteArrayRab barat2 = new ByteArrayRab(bytes);
    var thrown =
        assertThrows(IOException.class, () -> new EncryptedRab(types[1], barat2, secret, false));
    assertEquals("This is not an EncryptedRab", thrown.getMessage()); // Different header lengths.
  }

  @Test
  void testUnderlyingRandomAccessThingTooSmall() {
    byte[] bytes = new byte[10];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    var thrown =
        assertThrows(IOException.class, () -> new EncryptedRab(types[0], barat, secret, true));
    assertEquals(
        "Underlying RandomAccessBuffer is not long enough to include the footer.",
        thrown.getMessage());
    barat.close();
  }

  @Test
  void testWrongMagic() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    erat.close();
    ByteArrayRab barat2 = new ByteArrayRab(bytes);
    byte[] magic = ByteBuffer.allocate(8).putLong(FALSE_MAGIC).array();
    barat2.pwrite(types[0].headerLen - 8, magic, 0, 8);
    var thrown =
        assertThrows(IOException.class, () -> new EncryptedRab(types[0], barat2, secret, false));
    assertEquals("This is not an EncryptedRab", thrown.getMessage());
  }

  @Test
  void testWrongMasterSecret() throws IOException, GeneralSecurityException {
    byte[] bytes = new byte[100];
    ByteArrayRab barat = new ByteArrayRab(bytes);
    EncryptedRab erat = new EncryptedRab(types[0], barat, secret, true);
    erat.close();
    ByteArrayRab barat2 = new ByteArrayRab(barat.getBuffer());
    var thrown =
        assertThrows(
            GeneralSecurityException.class,
            () -> new EncryptedRab(types[0], barat2, new MasterSecret(), false));
    assertEquals("MAC is incorrect", thrown.getMessage());
  }

  @Test
  void testSuccesfulRoundTrip() throws IOException, GeneralSecurityException {
    for (var type : types) {
      byte[] bytes = new byte[100];
      ByteArrayRab barat = new ByteArrayRab(bytes);
      EncryptedRab erat = new EncryptedRab(type, barat, secret, true);
      erat.pwrite(0, message, 0, message.length);
      byte[] result = new byte[message.length];
      erat.pread(0, result, 0, result.length);
      erat.close();
      assertArrayEquals(message, result);
    }
  }

  private final Path base = Path.of("tmp.encrypted-random-access-thing-test");
}
