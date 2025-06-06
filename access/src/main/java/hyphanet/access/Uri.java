package hyphanet.access;

import hyphanet.access.key.AccessKey;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.Usk;
import hyphanet.access.key.client.ClientChk;
import hyphanet.access.key.client.ClientKsk;
import hyphanet.access.key.client.ClientSsk;
import hyphanet.base.Base64;
import hyphanet.base.CommonUtil;
import hyphanet.base.IllegalBase64Exception;
import hyphanet.support.URLDecoder;
import hyphanet.support.URLEncodedFormatException;
import hyphanet.support.URLEncoder;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public class Uri implements Serializable {

  public record Keys(
      @Nullable RoutingKey routingKey, @Nullable DecryptionKey decryptionKey, List<Byte> extra)
      implements Serializable {

    public Keys(
        @Nullable RoutingKey routingKey,
        @Nullable DecryptionKey decryptionKey,
        @Nullable byte[] extra) {
      this(routingKey, decryptionKey, CommonUtil.toByteList(extra));
    }

    public byte[] getExtraBytes() {
      return CommonUtil.toByteArray(extra);
    }
  }

  // Strip http(s):// and (web+|ext+)freenet: prefix
  protected static final Pattern URI_PREFIX =
      Pattern.compile("^(https?://[^/]+/+)?(((ext|web)\\+)?(freenet|hyphanet|hypha):)?");
  private static final char URI_SEPARATOR = '/';

  public Uri(String uri, boolean noTrim) throws MalformedURLException {

    if (!noTrim) {
      uri = uri.trim();
    }

    // Strip ?max-size, ?type etc.
    // Un-encoded ?'s are illegal.
    int x = uri.indexOf('?');
    if (x > -1) {
      uri = uri.substring(0, x);
    }

    if (uri.indexOf('@') < 0 || uri.indexOf(URI_SEPARATOR) < 0) {
      // Encoded URL?
      try {
        uri = URLDecoder.decode(uri, false);
      } catch (URLEncodedFormatException _) {
        throw new MalformedURLException(
            "Invalid URI: no @ or /, or @ or / is escaped but there are invalid " + "escapes");
      }
    }

    uri = URI_PREFIX.matcher(uri).replaceFirst("");

    // decode keyType
    int atChar = uri.indexOf('@');
    if (atChar == -1) {
      throw new MalformedURLException("There is no @ in that URI! (" + uri + ')');
    }

    String urlTypeStr = uri.substring(0, atChar).toUpperCase();
    uri = uri.substring(atChar + 1);

    try {
      uriType = KeyType.valueOf(urlTypeStr);
    } catch (IllegalArgumentException _) {
      throw new MalformedURLException("Invalid key type: " + urlTypeStr);
    }

    String uriPath;
    atChar = uri.indexOf(URI_SEPARATOR);
    if (atChar == -1) {
      // No '/' found, it's possibly a KSK
      keys = new Keys(null, null, List.of());
      uriPath = uri;
    } else {
      var keysStr = uri.substring(0, atChar);

      keys = parseKeysStr(keysStr);

      uriPath = uri.substring(atChar + 1);
    }

    metaStrings = parseMetaStrings(uriPath);
  }

  public Uri(String uri) throws MalformedURLException {
    this(uri, false);
  }

  public Uri(
      KeyType uriType,
      RoutingKey routingKey,
      DecryptionKey decryptionKey,
      byte[] extra,
      List<String> metaStrings) {

    this(uriType, new Keys(routingKey, decryptionKey, extra), metaStrings);
  }

  public Uri(KeyType uriType, Keys keys, List<String> metaStrings) {
    this.uriType = uriType;
    this.keys = keys;
    this.metaStrings = new ArrayList<>(metaStrings);
  }

  public KeyType getUriType() {
    return uriType;
  }

  public List<String> getMetaStrings() {
    return metaStrings;
  }

  public Keys getKeys() {
    return keys;
  }

  public AccessKey createAccessKey() throws MalformedURLException {
    return switch (uriType) {
      case USK -> new Usk(this);
      case KSK -> ClientKsk.create(this);
      case SSK -> new ClientSsk(this);
      case CHK -> new ClientChk(this);
    };
  }

  public String toString() {
    return toLongString(false, false);
  }

  public String toLongString(boolean prefix, boolean pureAscii) {
    StringBuilder sb = new StringBuilder();

    if (prefix) {
      sb.append("freenet:");
    }

    sb.append(uriType.name()).append('@');

    boolean hasKeys = false;

    var routingKey = keys.routingKey();
    if (routingKey != null) {
      sb.append(routingKey.toBase64());
      hasKeys = true;
    }

    var decryptionKey = keys.decryptionKey();
    if (decryptionKey != null) {
      sb.append(',').append(decryptionKey.toBase64());
    }

    if (!keys.extra().isEmpty()) {
      sb.append(',').append(Base64.encode(keys.getExtraBytes()));
    }

    var metaStringsSb = new StringBuilder();
    for (String metaString : metaStrings) {
      metaStringsSb
          .append(URI_SEPARATOR)
          .append(URLEncoder.encode(metaString, String.valueOf(URI_SEPARATOR), pureAscii));
    }

    if (!hasKeys) {
      // No keys, so we don't need to add the URI separator
      metaStringsSb.deleteCharAt(0);
    }

    sb.append(metaStringsSb);

    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Uri uri)) {
      return false;
    }
    return uriType == uri.uriType
        && Objects.equals(metaStrings, uri.metaStrings)
        && Objects.equals(keys, uri.keys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uriType, metaStrings, keys);
  }

  private Keys parseKeysStr(String keysStr) throws MalformedURLException {
    int commaPos;
    String routingKey = "";
    String cryptoKey = "";
    String extra = "";

    // Parse first comma
    commaPos = keysStr.indexOf(',');
    if (commaPos >= 0) {
      routingKey = keysStr.substring(0, commaPos);
      keysStr = keysStr.substring(commaPos + 1);

      // Parse second comma
      commaPos = keysStr.indexOf(',');
      if (commaPos >= 0) {
        cryptoKey = keysStr.substring(0, commaPos);
        keysStr = keysStr.substring(commaPos + 1);

        if (!keysStr.isEmpty()) {
          extra = keysStr;
        }
      }
    }

    if (!routingKey.isEmpty() && !cryptoKey.isEmpty() && !extra.isEmpty()) {
      try {
        return new Keys(
            RoutingKey.fromBase64(routingKey),
            DecryptionKey.fromBase64(cryptoKey),
            Base64.decode(extra));
      } catch (IllegalArgumentException | IllegalBase64Exception _) {
        throw new MalformedURLException(
            "Invalid URI: invalid routing key, crypto key or extra data");
      }
    } else {
      return new Keys(null, null, List.of());
    }
  }

  private static List<String> parseMetaStrings(String uriPath) {

    List<String> metaStrings = new ArrayList<>();

    if (uriPath.isEmpty()) {
      return metaStrings;
    }

    int start = 0;
    int end = 0;

    while (true) {
      // Skip all consecutive '/'
      while (start < uriPath.length() && uriPath.charAt(start) == URI_SEPARATOR) {
        start++;
      }

      // If we skipped any consecutive '/', add one empty string
      // As SSK@blah,blah,blah//filename is allowed with empty docname
      if (start > 1
          && start < uriPath.length()
          && uriPath.charAt(start - 1) == URI_SEPARATOR
          && uriPath.charAt(start - 2) == URI_SEPARATOR) {
        metaStrings.add("");
      }

      end = uriPath.indexOf(URI_SEPARATOR, start);

      if (end != -1) {
        metaStrings.add(uriPath.substring(start, end));
        start = end + 1;
      } else {
        if (start < uriPath.length()) {
          // Last part of the URI Path
          metaStrings.add(uriPath.substring(start));
        }
        break;
      }
    }

    return metaStrings;
  }

  private final KeyType uriType;
  private final List<String> metaStrings;
  private final Keys keys;
}
