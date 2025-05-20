package hyphanet.crypt.provider;

import org.bouncycastle.crypto.CipherKeyGenerator;
import org.bouncycastle.crypto.DefaultBufferedBlockCipher;
import org.bouncycastle.crypto.engines.RijndaelEngine;
import org.bouncycastle.crypto.modes.CFBBlockCipher;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseBlockCipher;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseKeyGenerator;

public final class Rijndael256 {
  public static class CFB extends BaseBlockCipher {
    public CFB() {
      super(
          new DefaultBufferedBlockCipher(CFBBlockCipher.newInstance(new RijndaelEngine(256), 256)),
          256);
    }
  }

  public static class KeyGen extends BaseKeyGenerator {
    public KeyGen() {
      super("Rijndael", 256, new CipherKeyGenerator());
    }
  }

  private Rijndael256() {}
}
