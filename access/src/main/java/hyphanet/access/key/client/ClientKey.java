package hyphanet.access.key.client;

import hyphanet.access.key.AccessKey;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.node.NodeKey;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.jspecify.annotations.Nullable;

public abstract class ClientKey extends AccessKey {

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

  public synchronized NodeKey getNodeKey(boolean cloneKey) {
    NodeKey nodeKey;
    if (cachedNodeKey != null) {
      nodeKey = cachedNodeKey;
    } else {
      nodeKey = createNodeKey();
      cachedNodeKey = nodeKey;
    }

    if (cloneKey) {
      var clazz = nodeKey.getClass();
      try {
        var ctor = clazz.getConstructor(clazz);
        nodeKey = ctor.newInstance(nodeKey);
      } catch (NoSuchMethodException
          | InstantiationException
          | IllegalAccessException
          | InvocationTargetException e) {
        throw new IllegalStateException(
            "Unable to call copy constructor of " + clazz + " - " + e.getMessage());
      }
    }
    return nodeKey;
  }

  public NodeKey getNodeKey() {
    return getNodeKey(true);
  }

  protected abstract NodeKey createNodeKey();

  private transient @Nullable NodeKey cachedNodeKey;
}
