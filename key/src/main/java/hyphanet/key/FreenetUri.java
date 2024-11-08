package hyphanet.key;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

public class FreenetUri implements Serializable {

    public enum UriType {
        USK, SSK, KSK, CHK
    }

    public FreenetUri(String uri, boolean noTrim) {
        Objects.requireNonNull(uri);

        if (!noTrim) {
            uri = uri.trim();
        }

        // Strip ?max-size, ?type etc.
        // Un-encoded ?'s are illegal.
        int x = uri.indexOf('?');
        if (x > -1)
            uri = uri.substring(0, x);

    }

    public FreenetUri(UriType uriType, byte[] routingKey, byte[] cryptoKey, byte[] extra, ArrayList<String> metaStrings) {
        Objects.requireNonNull(uriType);

        this.uriType = uriType;
        this.routingKey = routingKey;
        this.cryptoKey = cryptoKey;
        this.extra = extra;
        this.metaStrings = metaStrings;

    }

    private final UriType uriType;
    private final byte[] routingKey;
    private final byte[] cryptoKey;
    private final byte[] extra;
    private final ArrayList<String> metaStrings;
}
