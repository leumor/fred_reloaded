package hyphanet.access.key;

import hyphanet.access.DecryptionKey;
import hyphanet.access.Key;
import hyphanet.access.RoutingKey;
import hyphanet.access.Uri;

import java.util.List;

public interface User extends Key {
    Uri toUri();

    Uri toRequestUri();

    RoutingKey getRoutingKey();

    DecryptionKey getCryptoKey();

    List<String> getMetaStrings();
}
