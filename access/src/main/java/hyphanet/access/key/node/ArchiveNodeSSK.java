package hyphanet.access.key.node;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.RoutingKey;
import java.security.PublicKey;

public class ArchiveNodeSSK extends NodeSsk {
  public ArchiveNodeSSK(
      RoutingKey clientRoutingKey,
      CryptoAlgorithm cryptoAlgorithm,
      byte[] ehDocname,
      PublicKey publicKey) {
    super(clientRoutingKey, cryptoAlgorithm, ehDocname, publicKey);
  }
}
