package hyphanet.access.block.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import hyphanet.access.block.node.NodeChkBlock;
import hyphanet.access.key.CompressionAlgorithm;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.crypt.JcaProvider;
import hyphanet.support.io.storage.bucket.ArrayBucket;
import hyphanet.support.io.storage.bucket.ArrayBucketFactory;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientChkBlockTest {

  @BeforeAll
  static void setup() {
    Security.addProvider(new JcaProvider());
  }

  @Test
  void testEncodeDecodeEmptyBlock() throws Exception {
    byte[] buf = new byte[0];
    checkBlock(buf, false);
    checkBlock(buf, true);
  }

  @Test
  void testEncodeDecodeFullBlock() throws Exception {
    byte[] fullBlock = new byte[ClientChkBlock.DATA_LENGTH];
    var random = RandomGenerator.getDefault();
    for (int i = 0; i < 10; i++) {
      random.nextBytes(fullBlock);
      checkBlock(fullBlock, false);
      checkBlock(fullBlock, true);
    }
  }

  @Test
  void testEncodeDecodeShortInteger() throws Exception {
    for (int i = 0; i < 100; i++) {
      String s = Integer.toString(i);
      checkBlock(s.getBytes(StandardCharsets.UTF_8), false);
      checkBlock(s.getBytes(StandardCharsets.UTF_8), true);
    }
  }

  @Test
  void testEncodeDecodeRandomLength() throws Exception {
    var random = RandomGenerator.getDefault();
    for (int i = 0; i < 10; i++) {
      byte[] buf = new byte[random.nextInt(ClientChkBlock.DATA_LENGTH + 1)];
      random.nextBytes(buf);
      checkBlock(buf, false);
      checkBlock(buf, true);
    }
  }

  @Test
  void testEncodeDecodeNearlyFullBlock() throws Exception {
    var random = RandomGenerator.getDefault();
    for (int i = 0; i < 10; i++) {
      byte[] buf = new byte[ClientChkBlock.DATA_LENGTH - i];
      random.nextBytes(buf);
      checkBlock(buf, false);
      checkBlock(buf, true);
    }
    for (int i = 0; i < 10; i++) {
      byte[] buf = new byte[ClientChkBlock.DATA_LENGTH - (1 << i)];
      random.nextBytes(buf);
      checkBlock(buf, false);
      checkBlock(buf, true);
    }
  }

  private void checkBlock(byte[] data, boolean newAlgo) throws Exception {
    var cryptoAlgorithm =
        newAlgo
            ? CryptoAlgorithm.ALGO_AES_CTR_256_SHA256
            : CryptoAlgorithm.ALGO_AES_PCFB_256_SHA256;
    byte[] copyOfData = new byte[data.length];
    System.arraycopy(data, 0, copyOfData, 0, data.length);
    var encodedBlock =
        ClientChkBlock.encode(
            new ArrayBucket(data),
            false,
            false,
            CompressionAlgorithm.NO_COMP,
            data.length,
            null,
            null,
            cryptoAlgorithm);
    // Not modified in-place.
    assert (Arrays.equals(data, copyOfData));
    var key = encodedBlock.getClientKey();

    // Verify it.
    var block =
        new NodeChkBlock(
            encodedBlock.getBlock().getRawData(),
            encodedBlock.getBlock().getRawHeaders(),
            null,
            cryptoAlgorithm,
            true);
    var checkBlock = new ClientChkBlock(block, key);
    try (ArrayBucket checkData =
        (ArrayBucket) checkBlock.decode(new ArrayBucketFactory(), data.length, false)) {
      assertArrayEquals(checkData.toByteArray(), data);
    }
  }
}
