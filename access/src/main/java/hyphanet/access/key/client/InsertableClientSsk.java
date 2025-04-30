package hyphanet.access.key.client;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

public class InsertableClientSsk extends ClientSsk implements Insertable {

  public InsertableClientSsk(
      CryptoAlgorithm cryptoAlgorithm,
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      List<String> metaStrings,
      String docName,
      PublicKey publicKey,
      PrivateKey privateKey) {
    super(cryptoAlgorithm, routingKey, cryptoKey, metaStrings, docName, publicKey);
    this.privateKey = privateKey;
  }

  @Override
  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  private final PrivateKey privateKey;
}
