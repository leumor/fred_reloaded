package hyphanet.access.key.node;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.Key;
import hyphanet.access.key.RoutingKey;

public abstract class NodeKey extends Key {
  protected NodeKey(RoutingKey routingKey, CryptoAlgorithm cryptoAlgorithm) {
    super(routingKey, cryptoAlgorithm);
  }

  public byte[] getRoutingKeyBytes() {
    return getRoutingKey().bytes();
  }

  /**
   * Get key type
   *
   * <ul>
   *   <li>High 8 bit (<tt>(type >> 8) & 0xFF</tt>) is the base type ({@link NodeChk#BASE_TYPE} or
   *       {@link NodeChk#BASE_TYPE}).
   *   <li>Low 8 bit (<tt>type & 0xFF</tt>) is the crypto algorithm. (Currently only {@link
   *       CryptoAlgorithm#ALGO_AES_PCFB_256_SHA256} is supported).
   * </ul>
   */
  public abstract short getType();

  public abstract byte[] getFullKeyBytes();
}
