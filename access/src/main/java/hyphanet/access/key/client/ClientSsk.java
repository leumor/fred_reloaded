package hyphanet.access.key.client;

import hyphanet.access.KeyType;
import hyphanet.access.Uri;
import hyphanet.access.key.*;
import hyphanet.access.key.node.NodeSsk;
import hyphanet.crypt.Util;
import hyphanet.crypt.hash.Sha256;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class ClientSsk extends ClientKey<NodeSsk> implements SubspaceKey {
  public static final short EXTRA_LENGTH = 5;
  public static final int SSK_VERSION = 1;
  public static final char SEPARATOR = '-';

  public record ExtraData(CryptoAlgorithm cryptoAlgorithm) {
    public byte[] getExtraBytes() {
      // 5 bytes.
      byte[] extra = new byte[5];

      extra[0] = SSK_VERSION;
      extra[1] = 0; // 0 = fetch (public) URI; 1 = insert (private) URI
      extra[2] = (byte) cryptoAlgorithm.getValue();
      extra[3] = (byte) (1 >> 8); // was KeyBlock.HASH_SHA256 >> 8, but not used
      extra[4] = (byte) 1; // was KeyBlock.HASH_SHA256, but not used
      return extra;
    }
  }

  public ClientSsk(
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      String docName,
      @Nullable PublicKey publicKey) {
    this(routingKey, cryptoKey, cryptoAlgorithm, List.of(docName), publicKey);
  }

  public ClientSsk(
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      String docName,
      byte[] extra,
      @Nullable PublicKey publicKey)
      throws MalformedURLException {

    var metaStrings = new ArrayList<String>();
    metaStrings.add(docName);

    this(routingKey, cryptoKey, parseExtraData(extra).cryptoAlgorithm, metaStrings, publicKey);
  }

  public ClientSsk(
      RoutingKey routingKey, DecryptionKey cryptoKey, byte[] extra, List<String> metaStrings)
      throws MalformedURLException {
    this(routingKey, cryptoKey, parseExtraData(extra).cryptoAlgorithm, metaStrings, null);
  }

  public ClientSsk(Uri uri) throws MalformedURLException {
    if (uri.getUriType() != KeyType.SSK) {
      throw new MalformedURLException("Invalid URI type: " + uri.getUriType());
    }

    if (uri.getKeys().routingKey() == null) {
      throw new MalformedURLException("Routing key missing");
    }

    this(
        uri.getKeys().routingKey(),
        uri.getKeys().decryptionKey(),
        uri.getKeys().getExtraBytes(),
        uri.getMetaStrings());
  }

  public ClientSsk(
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      List<String> metaStrings,
      @Nullable PublicKey publicKey) {

    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No meta strings / document name given");
    }

    docName = metaStrings.removeFirst();

    super(routingKey, cryptoKey, cryptoAlgorithm, metaStrings);

    var md = Sha256.getMessageDigest();

    // verify publicKey
    if (publicKey != null) {
      var publicKeyBytes = publicKey.getEncoded();
      var publicKeyHash = md.digest(publicKeyBytes);
      if (!Arrays.equals(publicKeyHash, routingKey.getBytes())) {
        throw new IllegalArgumentException("Public key does not match routing key");
      }
    }
    this.publicKey = publicKey;

    // Calculate ehDocname
    try {
      ehDocname =
          Util.encryptWithRijndael(
              md.digest(docName.getBytes(StandardCharsets.UTF_8)), cryptoKey.getBytes());
    } catch (InvalidKeyException _) {
      throw new IllegalArgumentException("CryptoKey is invalid");
    }
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public String getDocName() {
    return docName;
  }

  public Uri toUri() {

    List<String> metaStrings = new ArrayList<>();
    metaStrings.add(docName);
    metaStrings.addAll(getMetaStrings());

    return new Uri(
        KeyType.SSK,
        getRoutingKey(),
        getDecryptionKey(),
        new ExtraData(getCryptoAlgorithm()).getExtraBytes(),
        metaStrings);
  }

  @Override
  public Uri toRequestUri() {
    return toUri();
  }

  @Override
  public NodeSsk getNodeKey() {
    return super.getNodeKey();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ClientSsk clientSsk)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(docName, clientSsk.docName)
        && Objects.deepEquals(ehDocname, clientSsk.ehDocname);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), docName, Arrays.hashCode(ehDocname));
  }

  public byte[] getEhDocname() {
    return ehDocname;
  }

  public Usk toUsk() {
    var matcher = DOC_NAME_WITH_EDITION_PATTERN.matcher(docName);
    if (!matcher.matches()) {
      throw new IllegalStateException("Invalid document name format: " + docName);
    }

    int offset = matcher.start(1) - 1;
    String siteName = docName.substring(0, offset);
    long edition = Long.parseLong(docName.substring(offset + 1));

    List<String> metaStrings = new ArrayList<>();
    metaStrings.add(siteName);
    metaStrings.add(String.valueOf(edition));
    metaStrings.addAll(getMetaStrings());

    return new Usk(getRoutingKey(), getDecryptionKey(), getCryptoAlgorithm(), metaStrings);
  }

  @Override
  protected NodeSsk createNodeKey() {
    return new NodeSsk(getRoutingKey(), getCryptoAlgorithm(), ehDocname, publicKey);
  }

  private static ExtraData parseExtraData(byte[] extra) throws MalformedURLException {
    if (extra.length < EXTRA_LENGTH) {
      throw new MalformedURLException("Extra bytes too short: " + extra.length + " bytes");
    }
    return new ExtraData(CryptoAlgorithm.fromValue(extra[2]));
  }

  private final String docName;
  private final PublicKey publicKey;

  /** Encrypted hashed docname */
  private final byte[] ehDocname;
}
