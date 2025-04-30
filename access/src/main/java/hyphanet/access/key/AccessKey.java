package hyphanet.access.key;

import hyphanet.access.Uri;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public abstract class AccessKey extends Key {
  protected AccessKey(
      RoutingKey routingKey,
      @Nullable DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      List<String> metaStrings) {
    super(routingKey, cryptoAlgorithm);
    this.cryptoKey = cryptoKey;
    this.metaStrings = metaStrings;
  }

  protected AccessKey(
      RoutingKey routingKey,
      @Nullable DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm,
      String metaString) {
    this(routingKey, cryptoKey, cryptoAlgorithm, List.of(metaString));
  }

  public DecryptionKey getCryptoKey() {
    return cryptoKey;
  }

  public List<String> getMetaStrings() {
    return metaStrings;
  }

  @Override
  public String toString() {
    return "%s,%s,%s,%s".formatted(getRoutingKey(), cryptoKey, getCryptoAlgorithm(), metaStrings);
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
    return Objects.equals(cryptoKey, accessKey.cryptoKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), cryptoKey);
  }

  private final @Nullable DecryptionKey cryptoKey;
  private final List<String> metaStrings;
}
