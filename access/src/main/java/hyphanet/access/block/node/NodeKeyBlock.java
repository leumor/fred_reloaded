/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.access.block.node;

import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.node.NodeKey;

import java.util.Arrays;
import java.util.Objects;

/**
 * Interface for fetched blocks. Can be decoded by using a ClientKey to construct a ClientKeyBlock,
 * which can then be decoded to a Bucket.
 */
public abstract class NodeKeyBlock<K extends NodeKey> {

  static final int HASH_SHA256 = 1;

  protected NodeKeyBlock(byte[] data, byte[] headers, K nodeKey, short hashIdentifier) {
    this.data = data;
    this.headers = headers;
    this.nodeKey = nodeKey;
    this.hashIdentifier = hashIdentifier;
  }

  public K getKey() {
    return nodeKey;
  }

  public byte[] getRawHeaders() {
    return headers;
  }

  public byte[] getRawData() {
    return data;
  }

  public RoutingKey getRoutingKey() {
    return nodeKey.getRoutingKey();
  }

  public byte[] getFullKeyBytes() {
    return nodeKey.getFullKeyBytes();
  }

  public abstract byte[] getPubkeyBytes();

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NodeKeyBlock<?> that)) {
      return false;
    }
    return Objects.deepEquals(data, that.data)
        && Objects.equals(nodeKey, that.nodeKey)
        && hashIdentifier == that.hashIdentifier;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(data), Arrays.hashCode(headers), nodeKey, hashIdentifier);
  }

  protected short getHashIdentifier() {
    return hashIdentifier;
  }

  private final byte[] data;
  private final byte[] headers;
  private final K nodeKey;

  private final short hashIdentifier;
}
