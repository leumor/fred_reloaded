package hyphanet.access.key;

import hyphanet.access.Uri;
import hyphanet.access.key.client.ClientSsk;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Usk extends AccessKey implements SubspaceKey {

  public Usk(
      RoutingKey routingKey,
      DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      List<String> metaStrings) {
    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No meta strings / document name given");
    }
    this.docName = metaStrings.removeFirst();

    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No suggested edition number");
    }

    try {
      this.suggestedEdition = Long.parseLong(metaStrings.removeFirst());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid suggested edition number: " + e, e);
    }

    super(routingKey, cryptoKey, cryptoAlgorithm, metaStrings);
  }

  public Usk(RoutingKey routingKey, DecryptionKey cryptoKey, byte[] extra, List<String> metaStrings)
      throws MalformedURLException {

    if (metaStrings.isEmpty()) {
      throw new IllegalArgumentException("No meta strings / document name given");
    }

    // Verify extra bytes, get cryptoAlgorithm - FIXME this should be a static method or something?
    var tmp = new ClientSsk(routingKey, cryptoKey, metaStrings.getFirst(), extra, null);

    this(routingKey, cryptoKey, tmp.getCryptoAlgorithm(), metaStrings);
  }

  public Usk(Uri uri) throws MalformedURLException {
    if (uri.getUriType() != Uri.UriType.USK || uri.getKeys() == null) {
      throw new MalformedURLException("Invalid URI type: " + uri.getUriType());
    }
    this(
        uri.getKeys().routingKey(),
        uri.getKeys().decryptionKey(),
        uri.getKeys().extra(),
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
        other.getCryptoKey(),
        other.getCryptoAlgorithm(),
        other.getMetaStrings());
  }

  @Override
  public Uri toUri() {
    var uskMetaStrings = List.of(docName, Long.toString(suggestedEdition));
    var fullMetaStrings =
        Stream.concat(uskMetaStrings.stream(), getMetaStrings().stream()).toList();

    return new Uri(
        Uri.UriType.USK,
        getRoutingKey(),
        getCryptoKey(),
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
