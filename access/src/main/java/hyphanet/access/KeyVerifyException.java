package hyphanet.access;

public class KeyVerifyException extends Exception {
  public KeyVerifyException(KeyType keyType, String message) {
    this(keyType, message, null);
  }

  public KeyVerifyException(KeyType keyType, String message, Throwable cause) {
    super(message, cause);
    this.keyType = keyType;
  }

  public KeyVerifyException(KeyType keyType) {
    this(keyType, (String) null);
  }

  public KeyVerifyException(KeyType keyType, Throwable cause) {
    this(keyType, null, cause);
  }

  public KeyType getKeyType() {
    return keyType;
  }

  private final KeyType keyType;
}
