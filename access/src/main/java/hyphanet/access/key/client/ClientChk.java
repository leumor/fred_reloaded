package hyphanet.access.key.client;

import hyphanet.access.KeyType;
import hyphanet.access.Uri;
import hyphanet.access.key.CompressionAlgorithm;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.node.NodeChk;
import hyphanet.access.key.node.NodeKey;
import java.net.MalformedURLException;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class ClientChk extends ClientKey {

  private static final short EXTRA_LENGTH = 5;

  public ClientChk(
      RoutingKey routingKey,
      @Nullable DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      @Nullable String fileName,
      boolean isControlDocument,
      CompressionAlgorithm compressionAlgorithm) {
    super(routingKey, cryptoKey, cryptoAlgorithm, fileName);
    this.isControlDocument = isControlDocument;
    this.compressionAlgorithm = compressionAlgorithm;
  }

  /**
   * Copy constructor.
   *
   * @param other
   */
  public ClientChk(ClientChk other) {
    this(
        other.getRoutingKey(),
        other.getCryptoKey(),
        other.getCryptoAlgorithm(),
        other.getFileName(),
        other.isControlDocument,
        other.compressionAlgorithm);
  }

  public ClientChk(
      RoutingKey routingKey,
      @Nullable DecryptionKey cryptoKey,
      byte[] extra,
      @Nullable String fileName)
      throws MalformedURLException {

    var extraData = parseExtraData(extra);

    this(
        routingKey,
        cryptoKey,
        extraData.cryptoAlgorithm,
        fileName,
        extraData.isControlDocument,
        extraData.compressionAlgorithm);
  }

  public ClientChk(Uri uri) throws MalformedURLException {
    if (uri.getUriType() != KeyType.CHK || uri.getKeys() == null) {
      throw new MalformedURLException("Invalid URI type: " + uri.getUriType());
    }

    var extraData = parseExtraData(uri.getKeys().extra());

    this(
        uri.getKeys().routingKey(),
        uri.getKeys().decryptionKey(),
        extraData.cryptoAlgorithm,
        !uri.getMetaStrings().isEmpty() ? uri.getMetaStrings().getFirst() : null,
        extraData.isControlDocument,
        extraData.compressionAlgorithm);
  }

  public boolean isControlDocument() {
    return isControlDocument;
  }

  @Override
  public Uri toUri() {
    return new Uri(KeyType.CHK, getRoutingKey(), getCryptoKey(), getExtraBytes(), getMetaStrings());
  }

  @Override
  public Uri toRequestUri() {
    return toUri();
  }

  public @Nullable String getFileName() {
    try {
      return getMetaStrings().getFirst();
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "%s: %s,%s".formatted(super.toString(), compressionAlgorithm, isControlDocument);
  }

  public byte[] getExtraBytes() {
    return new ExtraData(getCryptoAlgorithm(), isControlDocument, compressionAlgorithm)
        .getExtraBytes();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ClientChk clientChk)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return isControlDocument == clientChk.isControlDocument
        && compressionAlgorithm == clientChk.compressionAlgorithm;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), isControlDocument, compressionAlgorithm);
  }

  @Override
  public NodeChk getNodeKey() {
    return (NodeChk) super.getNodeKey();
  }

  @Override
  public NodeChk getNodeKey(boolean cloneKey) {
    return (NodeChk) super.getNodeKey(cloneKey);
  }

  public boolean isCompressed() {
    return compressionAlgorithm != CompressionAlgorithm.NO_COMP;
  }

  public CompressionAlgorithm getCompressionAlgorithm() {
    return compressionAlgorithm;
  }

  @Override
  protected NodeKey createNodeKey() {
    return new NodeChk(getRoutingKey(), getCryptoAlgorithm());
  }

  private static ExtraData parseExtraData(byte[] extra) throws MalformedURLException {
    if (extra.length < EXTRA_LENGTH) {
      throw new MalformedURLException("Invalid extra bytes in CHK - maybe a 0.5 key?");
    }

    // byte 0 is reserved, for now
    var cryptoAlgo = CryptoAlgorithm.fromValue(extra[1]);
    var controlDocument = (extra[2] & 0x02) != 0;
    var compressionAlgo =
        CompressionAlgorithm.fromValue((((extra[3] & 0xff) << 8) + (extra[4] & 0xff)));

    return new ExtraData(cryptoAlgo, controlDocument, compressionAlgo);
  }

  private record ExtraData(
      CryptoAlgorithm cryptoAlgorithm,
      boolean isControlDocument,
      CompressionAlgorithm compressionAlgorithm) {
    byte[] getExtraBytes() {
      byte[] extra = new byte[EXTRA_LENGTH];
      extra[0] = (byte) (cryptoAlgorithm.getValue() >> 8);
      extra[1] = (byte) cryptoAlgorithm.getValue();
      extra[2] = (byte) (isControlDocument() ? 2 : 0);
      extra[3] = (byte) (compressionAlgorithm.getValue() >> 8);
      extra[4] = (byte) compressionAlgorithm.getValue();
      return extra;
    }
  }

  /** Whether the data that this CHK refers to is a control document. */
  private final boolean isControlDocument;

  private final CompressionAlgorithm compressionAlgorithm;

  private transient NodeChk nodeKey;
}
