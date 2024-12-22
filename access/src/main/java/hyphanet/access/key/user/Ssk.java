package hyphanet.access.key.user;

import java.security.PublicKey;

public interface Ssk extends Client, SubspaceKey {
    char separator = '-';

    PublicKey getPublicKey();
}
