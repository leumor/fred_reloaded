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
}
