package hyphanet.access.key;

import java.util.Objects;

public abstract class Key {
  protected Key(RoutingKey routingKey, CryptoAlgorithm cryptoAlgorithm) {
    this.routingKey = routingKey;
    this.cryptoAlgorithm = cryptoAlgorithm;
  }

  public RoutingKey getRoutingKey() {
    return routingKey;
  }

  public CryptoAlgorithm getCryptoAlgorithm() {
    return cryptoAlgorithm;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Key key)) {
      return false;
    }
    return Objects.equals(getRoutingKey(), key.getRoutingKey())
        && getCryptoAlgorithm() == key.getCryptoAlgorithm();
  }

  @Override
  public int hashCode() {
    return Objects.hash(routingKey, cryptoAlgorithm);
  }

  private final RoutingKey routingKey;
  private final CryptoAlgorithm cryptoAlgorithm;
}
