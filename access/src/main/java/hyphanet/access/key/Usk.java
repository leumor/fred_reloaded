package hyphanet.access.key;

import hyphanet.access.Uri;
import java.util.List;

public class Usk extends AccessKey implements SubspaceKey {

  public Usk(
      CryptoAlgorithm cryptoAlgorithm,
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      List<String> metaStrings) {
    super(routingKey, cryptoKey, cryptoAlgorithm, metaStrings);
  }

  @Override
  public Uri toUri() {
    return null;
  }

  @Override
  public Uri toRequestUri() {
    return null;
  }

  @Override
  public String getDocName() {
    return "";
  }
}
