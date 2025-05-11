package hyphanet.access.key.client;

import hyphanet.access.key.AccessKey;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.node.NodeKey;
import java.util.List;
import org.jspecify.annotations.Nullable;

public abstract class ClientKey<N extends NodeKey<N>> extends AccessKey {

  protected ClientKey(
      RoutingKey routingKey,
      @Nullable DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      String metaString) {
    super(routingKey, cryptoKey, cryptoAlgorithm, metaString);
  }

  protected ClientKey(
      RoutingKey routingKey,
      @Nullable DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      List<String> metaStrings) {
    super(routingKey, cryptoKey, cryptoAlgorithm, metaStrings);
  }

  public synchronized N getNodeKey(boolean cloneKey) {
    N nodeKey;
    if (cachedNodeKey != null) {
      nodeKey = cachedNodeKey;
    } else {
      nodeKey = createNodeKey();
      cachedNodeKey = nodeKey;
    }

    return cloneKey ? nodeKey.copy() : nodeKey;
  }

  public N getNodeKey() {
    return getNodeKey(true);
  }

  protected abstract N createNodeKey();

  private transient @Nullable N cachedNodeKey;
}
