package hyphanet.access.key.client;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import java.security.PrivateKey;
import java.security.PublicKey;

public class InsertableClientSsk extends ClientSsk implements Insertable {

  public InsertableClientSsk(
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      String docName,
      PublicKey publicKey,
      PrivateKey privateKey) {
    super(routingKey, cryptoKey, cryptoAlgorithm, docName, publicKey);
    this.privateKey = privateKey;
  }

  @Override
  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  private final PrivateKey privateKey;
}
