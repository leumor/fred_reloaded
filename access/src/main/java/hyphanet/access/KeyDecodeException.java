package hyphanet.access;

public class KeyDecodeException extends Exception {
  public KeyDecodeException(KeyType keyType, String message) {
    this(keyType, message, null);
  }

  public KeyDecodeException(KeyType keyType, String message, Throwable cause) {
    super(message, cause);
    this.keyType = keyType;
  }

  public KeyDecodeException(KeyType keyType) {
    this(keyType, (String) null);
  }

  public KeyDecodeException(KeyType keyType, Throwable cause) {
    this(keyType, null, cause);
  }

  public KeyType getKeyType() {
    return keyType;
  }

  private final KeyType keyType;
}
