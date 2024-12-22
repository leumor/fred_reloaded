package hyphanet.access;

import hyphanet.support.Base64;
import hyphanet.support.IllegalBase64Exception;

public record DecryptionKey(byte[] bytes) {
    public static final int CRYPTO_KEY_LENGTH = 32;

    public DecryptionKey {
        if (bytes.length != CRYPTO_KEY_LENGTH) {
            throw new IllegalArgumentException("Crypto key must be 32 bytes");
        }
    }

    public static DecryptionKey fromBase64(String base64Str) {
        try {
            var bytes = Base64.decode(base64Str);
            return new DecryptionKey(bytes);
        } catch (IllegalBase64Exception e) {
            throw new IllegalArgumentException("Invalid base64 crypto key", e);
        }
    }
}
