package hyphanet.access.key;

import hyphanet.access.KeyType;
import hyphanet.access.Uri;
import hyphanet.access.key.client.ClientSsk;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Usk extends AccessKey implements SubspaceKey {

  public Usk(
      RoutingKey routingKey,
      DecryptionKey decryptionKey,
      CryptoAlgorithm cryptoAlgorithm,
      List<String> metaStrings) {

    metaStrings = new ArrayList<>(metaStrings); // copy to avoid modifying the original list

    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No meta strings / document name given");
    }
    docName = metaStrings.removeFirst();

    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No suggested edition number");
    }

    try {
      suggestedEdition = Long.parseLong(metaStrings.removeFirst());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid suggested edition number: " + e, e);
    }

    super(routingKey, decryptionKey, cryptoAlgorithm, metaStrings);
  }

  public Usk(
      RoutingKey routingKey, DecryptionKey decryptionKey, byte[] extra, List<String> metaStrings)
      throws MalformedURLException {

    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No meta strings / document name given");
    }

    // Verify extra bytes, get cryptoAlgorithm - FIXME this should be a static method or something?
    var tmp = new ClientSsk(routingKey, decryptionKey, metaStrings.getFirst(), extra, null);

    this(routingKey, decryptionKey, tmp.getCryptoAlgorithm(), metaStrings);
  }

  public Usk(Uri uri) throws MalformedURLException {
    if (uri.getUriType() != KeyType.USK || uri.getKeys() == null) {
      throw new MalformedURLException("Invalid URI type: " + uri.getUriType());
    }

    this(
        uri.getKeys().routingKey(),
        uri.getKeys().decryptionKey(),
        uri.getKeys().getExtraBytes(),
        uri.getMetaStrings());
  }

  /**
   * Copy constructor.
   *
   * @param other The Usk instance to copy from
   */
  public Usk(Usk other) {
    this(
        other.getRoutingKey(),
        other.getDecryptionKey(),
        other.getCryptoAlgorithm(),
        other.getMetaStrings());
  }

  @Override
  public Uri toUri() {
    var uskMetaStrings = List.of(docName, String.valueOf(suggestedEdition));
    var fullMetaStrings =
        Stream.concat(uskMetaStrings.stream(), getMetaStrings().stream()).toList();

    return new Uri(
        KeyType.USK,
        getRoutingKey(),
        getDecryptionKey(),
        new ClientSsk.ExtraData(getCryptoAlgorithm()).getExtraBytes(),
        fullMetaStrings);
  }

  @Override
  public Uri toRequestUri() {
    return toUri();
  }

  @Override
  public String getDocName() {
    return docName;
  }

  public ClientSsk toSsk() {

    var edition = Math.abs(suggestedEdition);
    if (edition == Long.MIN_VALUE) {
      edition = Long.MAX_VALUE;
    }

    List<String> fullMetaStrings = new ArrayList<>();
    fullMetaStrings.add(docName + "-" + edition);
    fullMetaStrings.addAll(getMetaStrings());

    try {
      return new ClientSsk(
          getRoutingKey(),
          getDecryptionKey(),
          new ClientSsk.ExtraData(getCryptoAlgorithm()).getExtraBytes(),
          fullMetaStrings);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Usk usk)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return suggestedEdition == usk.suggestedEdition && Objects.equals(docName, usk.docName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), docName, suggestedEdition);
  }

  private final String docName; // site name
  private final long suggestedEdition;
}
