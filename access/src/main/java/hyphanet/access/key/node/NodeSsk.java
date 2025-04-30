package hyphanet.access.key.node;

import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.RoutingKey;
import hyphanet.crypt.hash.Sha256;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

public class NodeSsk extends NodeKey {
  public static final int E_H_DOCNAME_SIZE = 32;
  public static final byte BASE_TYPE = 2;
  public static final int FULL_KEY_LENGTH = 66;

  public NodeSsk(
      RoutingKey clientRoutingKey,
      CryptoAlgorithm cryptoAlgorithm,
      byte[] ehDocname,
      PublicKey publicKey) {
    super(makeRoutingKey(clientRoutingKey, ehDocname), cryptoAlgorithm);
    this.ehDocname = ehDocname;
    this.publicKey = publicKey;
    this.clientRoutingKey = clientRoutingKey;

    // verify publicKey
    if (publicKey != null) {
      var publicKeyBytes = publicKey.getEncoded();
      var publicKeyHash = Sha256.digest(publicKeyBytes);
      if (!Arrays.equals(clientRoutingKey.bytes(), publicKeyHash)) {
        throw new IllegalArgumentException("Public key does not match client routing key");
      }
    }

    if (ehDocname.length != E_H_DOCNAME_SIZE) {
      throw new IllegalArgumentException("ehDocname must be " + E_H_DOCNAME_SIZE + " bytes");
    }
  }

  public NodeSsk(RoutingKey clientRoutingKey, CryptoAlgorithm cryptoAlgorithm, byte[] ehDocname) {
    this(clientRoutingKey, cryptoAlgorithm, ehDocname, null);
  }

  public NodeSsk(NodeSsk other) {
    this(other.clientRoutingKey, other.getCryptoAlgorithm(), other.ehDocname, other.publicKey);
  }

  // routingKey = H( E(H(docname)) + H(pubkey) )
  private static RoutingKey makeRoutingKey(RoutingKey clientRoutingKey, byte[] ehDocname) {
    MessageDigest md256 = Sha256.getMessageDigest();
    md256.update(ehDocname);
    md256.update(clientRoutingKey.bytes());
    return new RoutingKey(md256.digest());
  }

  /** E(H(docname)) (E = encrypt using decrypt key, which only clients know) */
  private final byte[] ehDocname;

  private @Nullable PublicKey publicKey;
  private RoutingKey clientRoutingKey; // Was pubKeyHash
}
