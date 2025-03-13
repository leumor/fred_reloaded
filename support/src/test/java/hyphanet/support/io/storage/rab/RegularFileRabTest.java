package hyphanet.support.io.storage.rab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.BucketTools;
import hyphanet.support.io.util.FileSystem;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegularFileRabTest extends RabTestBase {

  private static final int[] TEST_LIST =
      new int[] {0, 1, 32, 64, 32768, 1024 * 1024, 1024 * 1024 + 1};

  RegularFileRabTest() {
    super(TEST_LIST);
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
  void testStoreTo() throws IOException, StorageFormatException, ResumeFailedException {
    Path tempFile = Files.createTempFile(base, "test-storeto", ".tmp");
    byte[] buf = new byte[4096];
    Random r = new Random(1267612);
    r.nextBytes(buf);
    RegularFileRab rafw = new RegularFileRab(tempFile, buf.length, false);
    rafw.pwrite(0, buf, 0, buf.length);
    byte[] tmp = new byte[buf.length];
    rafw.pread(0, tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    rafw.storeTo(dos);
    dos.close();
    rafw.close();
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
    RegularFileRab restored = (RegularFileRab) BucketTools.restoreRabFrom(dis, null, null, null);
    assertEquals(buf.length, restored.size());
    assertEquals(rafw, restored);
    tmp = new byte[buf.length];
    restored.pread(0, tmp, 0, buf.length);
    assertArrayEquals(buf, tmp);
    restored.close();
    restored.dispose();
  }

  @Override
  protected Rab construct(long size) throws IOException {
    Path f = Files.createTempFile(base, "test", ".tmp");
    return new RegularFileRab(f, size, false);
  }

  private final Path base = Path.of("tmp.random-access-file-wrapper-test");
}
