package hyphanet.access.block.node;

import hyphanet.access.KeyType;
import hyphanet.access.KeyVerifyException;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.node.NodeChk;
import hyphanet.crypt.hash.Sha256;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class NodeChkBlock extends NodeKeyBlock<NodeChk> {
  public static final int TOTAL_HEADERS_LENGTH = 36;

  protected NodeChkBlock(
      byte[] data,
      byte[] headers,
      @Nullable NodeChk nodeKey,
      CryptoAlgorithm cryptoAlgorithm,
      boolean verify)
      throws KeyVerifyException {

    if (headers.length != TOTAL_HEADERS_LENGTH) {
      throw new IllegalArgumentException(
          "Wrong length: %d should be %d".formatted(headers.length, TOTAL_HEADERS_LENGTH));
    }

    var hashIdentifier = (short) (((headers[0] & 0xff) << 8) + (headers[1] & 0xff));

    var md = Sha256.getMessageDigest();
    md.update(headers);
    md.update(data);
    var hash = md.digest();

    if (nodeKey == null) {
      nodeKey = new NodeChk(new RoutingKey(hash), cryptoAlgorithm);
    }

    if (verify) {
      // Check hash
      if (hashIdentifier != HASH_SHA256) {
        throw new KeyVerifyException(KeyType.CHK, "Hash is not SHA-256");
      }

      // Check the routing key
      if (!Arrays.equals(hash, nodeKey.getRoutingKey().bytes())) {
        throw new KeyVerifyException(KeyType.CHK, "Routing key does not match hash");
      }
    }

    super(data, headers, nodeKey, hashIdentifier);
  }

  @Override
  public byte[] getPubkeyBytes() {
    return new byte[0];
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NodeChkBlock that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.deepEquals(getRawHeaders(), that.getRawHeaders());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), Arrays.hashCode(getRawHeaders()));
  }
}
