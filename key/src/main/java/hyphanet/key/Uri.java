package hyphanet.key;

import hyphanet.support.Base64;
import hyphanet.support.IllegalBase64Exception;
import hyphanet.support.URLDecoder;
import hyphanet.support.URLEncodedFormatException;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Uri implements Serializable {


    public enum UriType {
        USK, SSK, KSK, CHK
    }

    public record Keys(RoutingKey routingKey, CryptoKey cryptoKey, byte[] extra) {
        public Keys {
            if (routingKey == null || cryptoKey == null || extra == null) {
                throw new IllegalArgumentException(
                    "Routing key, crypto key and extra data must not be null");
            }
        }
    }

    // Strip http(s):// and (web+|ext+)freenet: prefix
    protected final static Pattern URI_PREFIX =
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
            } catch (URLEncodedFormatException e) {
                throw new MalformedURLException(
                    "Invalid URI: no @ or /, or @ or / is escaped but there are invalid escapes");
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
            uriType = UriType.valueOf(urlTypeStr);
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Invalid key type: " + urlTypeStr);
        }

        String uriPath;
        atChar = uri.indexOf(URI_SEPARATOR);
        if (atChar == -1) {
            // No '/' found, it's possibly a KSK
            keys = null;
            uriPath = uri;
        } else {
            var keysStr = uri.substring(0, atChar);

            keys = parseKeysStr(keysStr);

            uriPath = uri.substring(atChar + 1);
        }

        metaStrings = parseMetaStrings(uriPath);
    }

    public Uri(
        UriType uriType, RoutingKey routingKey, CryptoKey cryptoKey, byte[] extra,
        String[] metaStrings) {

        this(uriType, new Keys(routingKey, cryptoKey, extra), metaStrings);
    }

    public Uri(UriType uriType, Keys keys, String[] metaStrings) {
        this.uriType = uriType;
        this.keys = keys;
        this.metaStrings = metaStrings;
    }

    public UriType getUriType() {
        return uriType;
    }

    public String[] getMetaStrings() {
        return metaStrings;
    }

    public @Nullable Keys getKeys() {
        return keys;
    }

    private @Nullable Keys parseKeysStr(String keysStr) throws MalformedURLException {
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
                return new Keys(RoutingKey.fromBase64(routingKey), CryptoKey.fromBase64(cryptoKey),
                                Base64.decode(extra));
            } catch (IllegalArgumentException | IllegalBase64Exception e) {
                throw new MalformedURLException(
                    "Invalid URI: invalid routing key, crypto key or extra data");
            }
        } else {
            return null;
        }
    }

    private static String[] parseMetaStrings(String uriPath) {

        if (uriPath.isEmpty()) {
            return new String[0];
        }

        List<String> metaStrings = new ArrayList<>();

        int start = 0;
        int end = 0;

        while (true) {
            // Skip all consecutive '/'
            while (start < uriPath.length() && uriPath.charAt(start) == URI_SEPARATOR) {
                start++;
            }

            // If we skipped any consecutive '/', add one empty string
            // As SSK@blah,blah,blah//filename is allowed with empty docname
            if (start > 1 && start < uriPath.length() && uriPath.charAt(start - 1) == URI_SEPARATOR &&
                uriPath.charAt(start - 2) == URI_SEPARATOR) {
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

        return metaStrings.toArray(String[]::new);
    }

    private final UriType uriType;
    private final String[] metaStrings;
    private final @Nullable Keys keys;
}
