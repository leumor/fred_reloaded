package hyphanet.support.io.storage.rab;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.storage.EncryptType;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;

public class EncryptedRabAltTest extends RabTestBase {

  private static final EncryptType[] types = EncryptType.values();

  private static final MasterSecret secret = new MasterSecret();
  private static final int[] TEST_LIST =
      new int[] {0, 1, 32, 64, 32768, 1024 * 1024, 1024 * 1024 + 1};

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  public EncryptedRabAltTest() {
    super(TEST_LIST);
  }

  @Override
  protected Rab construct(long size) throws IOException {
    ArrayRab barat = new ArrayRab((int) (size + types[0].headerLen));
    try {
      return new EncryptedRab(types[0], barat, secret, true);
    } catch (GeneralSecurityException e) {
      throw new Error(e);
    }
  }
}
