package hyphanet.access.key.client;

import hyphanet.access.Uri;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import hyphanet.crypt.Global;
import hyphanet.crypt.hash.Sha256;
import hyphanet.crypt.key.DsaPublicKeyWithMpiFormat;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.DSAPublicKey;

public class ClientKsk extends InsertableClientSsk {
  public ClientKsk(
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      String docName,
      PublicKey publicKey,
      PrivateKey privateKey) {

    super(
        routingKey,
        cryptoKey,
        CryptoAlgorithm.ALGO_AES_PCFB_256_SHA256,
        docName,
        publicKey,
        privateKey);
  }

  public static ClientKsk create(String keyword) {

    KeyPair keyPair;
    try {
      var keyGen = KeyPairGenerator.getInstance("DSA");
      keyGen.initialize(Global.DSA_GROUP_BIG_A);
      keyPair = keyGen.generateKeyPair();
    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
      throw new IllegalStateException(e);
    }

    var privKey = keyPair.getPrivate();
    var pubKey =
        new DsaPublicKeyWithMpiFormat((DSAPublicKey) keyPair.getPublic(), Global.DSA_GROUP_BIG_A);

    var md = Sha256.getMessageDigest();
    byte[] keywordHash = md.digest(keyword.getBytes(StandardCharsets.UTF_8));
    byte[] pubKeyHash = md.digest(pubKey.getEncoded());

    return new ClientKsk(
        new RoutingKey(pubKeyHash), new DecryptionKey(keywordHash), keyword, pubKey, privKey);
  }

  public static ClientKsk create(Uri uri) throws MalformedURLException {

    var metaStrings = uri.getMetaStrings();
    if (metaStrings.isEmpty()) {
      throw new MalformedURLException("No meta strings / document name given");
    }

    var docName = metaStrings.getFirst();

    if (docName.isEmpty()) {
      throw new MalformedURLException("No document name given");
    }

    return create(docName);
  }
}
