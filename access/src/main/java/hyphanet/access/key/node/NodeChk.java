package hyphanet.access.key.node;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.RoutingKey;

public class NodeChk extends NodeKey<NodeChk> {
  public static final byte BASE_TYPE = 1;

  /** 32 bytes for hash, 2 bytes for type */
  public static final short FULL_KEY_LENGTH = 34;

  public NodeChk(RoutingKey routingKey, CryptoAlgorithm cryptoAlgorithm) {
    super(routingKey, cryptoAlgorithm);
  }

  /**
   * Copy constructor.
   *
   * @param other The NodeChk instance to copy from
   */
  public NodeChk(NodeChk other) {
    super(other.getRoutingKey(), other.getCryptoAlgorithm());
  }

  @Override
  public short getType() {
    return (short) ((BASE_TYPE << 8) + (getCryptoAlgorithm().getValue() & 0xFF));
  }

  @Override
  public byte[] getFullKeyBytes() {
    byte[] buf = new byte[FULL_KEY_LENGTH];
    short type = getType();
    buf[0] = (byte) (type >> 8);
    buf[1] = (byte) (type & 0xFF);
    var routingKeyBytes = getRoutingKey().bytes();
    System.arraycopy(routingKeyBytes, 0, buf, 2, routingKeyBytes.length);
    return buf;
  }

  @Override
  public NodeChk copy() {
    return new NodeChk(this);
  }
}
