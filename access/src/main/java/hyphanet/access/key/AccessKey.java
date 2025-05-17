package hyphanet.access.key;

import hyphanet.access.Uri;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public abstract class AccessKey extends Key {
  protected AccessKey(
      RoutingKey routingKey,
      @Nullable DecryptionKey decryptionKey,
      CryptoAlgorithm cryptoAlgorithm,
      List<String> metaStrings) {
    super(routingKey, cryptoAlgorithm);
    this.decryptionKey = decryptionKey;
    this.metaStrings = metaStrings;
  }

  protected AccessKey(
      RoutingKey routingKey,
      @Nullable DecryptionKey decryptionKey,
      CryptoAlgorithm cryptoAlgorithm,
      String metaString) {
    this(routingKey, decryptionKey, cryptoAlgorithm, List.of(metaString));
  }

  public DecryptionKey getDecryptionKey() {
    return decryptionKey;
  }

  public List<String> getMetaStrings() {
    return metaStrings;
  }

  @Override
  public String toString() {
    return "%s,%s,%s,%s"
        .formatted(getRoutingKey(), decryptionKey, getCryptoAlgorithm(), metaStrings);
  }

  public abstract Uri toUri();

  public abstract Uri toRequestUri();

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AccessKey accessKey)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(decryptionKey, accessKey.decryptionKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), decryptionKey);
  }

  private final @Nullable DecryptionKey decryptionKey;
  private final List<String> metaStrings;
}
