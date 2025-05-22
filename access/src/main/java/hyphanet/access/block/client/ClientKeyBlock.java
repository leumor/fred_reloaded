package hyphanet.access.block.client;

import hyphanet.access.KeyDecodeException;
import hyphanet.access.block.node.NodeKeyBlock;
import hyphanet.access.key.client.ClientKey;
import hyphanet.access.key.node.NodeKey;
import hyphanet.support.io.storage.bucket.*;
import java.io.IOException;
import java.util.Objects;

public abstract class ClientKeyBlock<
    N extends NodeKey<N>, C extends ClientKey<N>, B extends NodeKeyBlock<N>> {

  protected ClientKeyBlock(C clientKey, B block) {
    this.clientKey = clientKey;
    this.block = block;
  }

  /**
   * @return The ClientKey for this key.
   */
  public C getClientKey() {
    return clientKey;
  }

  /**
   * @return The underlying KeyBlock.
   */
  public B getBlock() {
    return block;
  }

  /**
   * @return The low-level Key for the block.
   */
  public NodeKey<N> getKey() {
    return block.getKey();
  }

  public byte[] memoryEncode() throws KeyDecodeException, IOException {
    return memoryDecode(false);
  }

  public byte[] memoryDecode(boolean dontDecompress) throws KeyDecodeException, IOException {
    ArrayBucket a = (ArrayBucket) decode(new ArrayBucketFactory(), 32 * 1024, dontDecompress);
    return BucketTools.toByteArray(a); // FIXME
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ClientKeyBlock<?, ?, ?> that)) {
      return false;
    }
    return Objects.equals(clientKey, that.clientKey) && Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientKey, block);
  }

  /**
   * Decode with the key
   *
   * @param factory The BucketFactory to use to create the Bucket to return the data in.
   * @param maxLength The maximum size of the returned data in bytes.
   */
  public abstract Bucket decode(BucketFactory factory, int maxLength, boolean dontDecompress)
      throws KeyDecodeException, IOException;

  /** Does the block contain metadata? If not, it contains real data. */
  public abstract boolean isMetadata();

  private final C clientKey;
  private final B block;
}
