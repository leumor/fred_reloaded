package hyphanet.crypt.key;

import hyphanet.crypt.Util;
import java.io.Serial;
import java.math.BigInteger;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAParameterSpec;

public class DsaPublicKeyWithMpiFormat implements DSAPublicKey {
  @Serial private static final long serialVersionUID = 1L;

  public DsaPublicKeyWithMpiFormat(DSAPublicKey key, DSAParameterSpec spec) {
    this.delegate = key;
    this.spec = spec;
  }

  @Override
  public String getAlgorithm() {
    return delegate.getAlgorithm();
  }

  @Override
  public String getFormat() {
    // Return whatever format name you choose
    return "MPI";
  }

  @Override
  public byte[] getEncoded() {
    // Calculate group bytes
    byte[] pBytes = Util.calcMpiBytes(spec.getP());
    byte[] qBytes = Util.calcMpiBytes(spec.getQ());
    byte[] gBytes = Util.calcMpiBytes(spec.getG());
    byte[] groupBytes = new byte[pBytes.length + qBytes.length + gBytes.length];
    System.arraycopy(pBytes, 0, groupBytes, 0, pBytes.length);
    System.arraycopy(qBytes, 0, groupBytes, pBytes.length, qBytes.length);
    System.arraycopy(gBytes, 0, groupBytes, pBytes.length + qBytes.length, gBytes.length);

    byte[] yBytes = Util.calcMpiBytes(getY());

    byte[] bytes = new byte[groupBytes.length + yBytes.length];
    System.arraycopy(groupBytes, 0, bytes, 0, groupBytes.length);
    System.arraycopy(yBytes, 0, bytes, groupBytes.length, yBytes.length);

    return bytes;
  }

  @Override
  public DSAParams getParams() {
    return delegate.getParams();
  }

  @Override
  public BigInteger getY() {
    return delegate.getY();
  }

  private final transient DSAParameterSpec spec;
  private final DSAPublicKey delegate;
}
