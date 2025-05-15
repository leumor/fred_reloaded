package hyphanet.access.key.client;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import java.security.PrivateKey;
import java.security.PublicKey;

public class ClientKsk extends InsertableClientSsk {
  public ClientKsk(
      CryptoAlgorithm cryptoAlgorithm,
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      String docName,
      PublicKey publicKey,
      PrivateKey privateKey) {
    super(routingKey, cryptoKey, cryptoAlgorithm, docName, publicKey, privateKey);
  }
}
