package hyphanet.access.key.user;

import hyphanet.access.key.User;

public interface Chk extends User, Client {
    boolean hasControlDocument();
}
