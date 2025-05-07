package hyphanet.access;

import org.jspecify.annotations.Nullable;

public class KeyEncodeException extends Exception {

  public KeyEncodeException(String message) {
    this(message, null);
  }

  public KeyEncodeException() {
    this((String) null, null);
  }

  public KeyEncodeException(String message, Throwable cause) {
    super(message, cause);
    this.keyType = null;
  }

  public KeyEncodeException(Throwable cause) {
    this((String) null, cause);
  }

  public KeyEncodeException(KeyType keyType, String message) {
    this(keyType, message, null);
  }

  public KeyEncodeException(KeyType keyType, String message, Throwable cause) {
    super(message, cause);
    this.keyType = keyType;
  }

  public KeyEncodeException(KeyType keyType) {
    this(keyType, (String) null);
  }

  public KeyEncodeException(KeyType keyType, Throwable cause) {
    this(keyType, null, cause);
  }

  public KeyType getKeyType() {
    return keyType;
  }

  private final @Nullable KeyType keyType;
}
