package hyphanet.crypt;

import java.security.Provider;

public class JcaProvider extends Provider {
    public JcaProvider() {
        super("Hyphanet Jce Provider", "1.0",
              "Hyphanet specified ciphers such as Rijndael with CFB mode");

        put("Cipher.RIJNDAEL256/CFB/NoPadding", "hyphanet.crypt.provider.Rijndael256$CFB");
        put("KeyGenerator.RIJNDAEL256", "hyphanet.crypt.provider.Rijndael256$KeyGen");
    }
}
