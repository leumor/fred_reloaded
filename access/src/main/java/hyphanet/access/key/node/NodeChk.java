package hyphanet.access.key.node;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.RoutingKey;

public class NodeChk extends NodeKey {
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
}
