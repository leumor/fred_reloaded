package hyphanet.access.block.node;

import hyphanet.access.KeyType;
import hyphanet.access.KeyVerifyException;
import hyphanet.access.key.node.NodeSsk;
import hyphanet.base.HexUtil;
import hyphanet.crypt.Global;
import hyphanet.crypt.hash.Sha256;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.interfaces.DSAPublicKey;
import java.util.Arrays;
import java.util.Objects;
import org.bouncycastle.crypto.params.DSAPublicKeyParameters;
import org.bouncycastle.crypto.signers.DSASigner;

public class NodeSskBlock extends NodeKeyBlock<NodeSsk> {
  public static final short DATA_LENGTH = 1024;
  /* Maximum length of compressed payload */
  public static final int MAX_COMPRESSED_DATA_LENGTH = DATA_LENGTH - 2;
  public static final short ENCRYPTED_HEADERS_LENGTH = 36;
  public static final int DATA_DECRYPT_KEY_LENGTH = 32;
  private static final short SIG_R_LENGTH = 32;
  private static final short SIG_S_LENGTH = 32;
  private static final short E_H_DOCNAME_LENGTH = 32;
  public static final short TOTAL_HEADERS_LENGTH =
      2 + SIG_R_LENGTH + SIG_S_LENGTH + 2 + E_H_DOCNAME_LENGTH + DATA_DECRYPT_KEY_LENGTH + 2 + 2;
  // how much of the headers we compare in order to consider two
  // SSKBlocks equal - necessary because the last 64 bytes need not
  // be the same for the same data and the same key (see comments below)
  private static final int HEADER_COMPARE_TO = 71;

  /**
   * HEADERS FORMAT: 2 bytes - hash ID 2 bytes - symmetric cipher ID 32 bytes - E(H(docname))
   * ENCRYPTED WITH E(H(docname)) AS IV: 32 bytes - H(decrypted data), = data decryption key 2 bytes
   * - data length + metadata flag 2 bytes - data compression algorithm or -1 IMPLICIT - hash of
   * data IMPLICIT - hash of remaining fields, including the implicit hash of data
   *
   * <p>SIGNATURE ON THE ABOVE HASH: 32 bytes - signature: R (unsigned bytes) 32 bytes - signature:
   * S (unsigned bytes)
   *
   * <p>PLUS THE PUBKEY: Pubkey Group
   */
  public NodeSskBlock(byte[] data, byte[] headers, NodeSsk nodeKey, boolean verify)
      throws KeyVerifyException {
    if (headers.length != TOTAL_HEADERS_LENGTH) {
      throw new IllegalArgumentException(
          "Headers.length=%d should be %s".formatted(headers.length, TOTAL_HEADERS_LENGTH));
    }

    if (data.length != DATA_LENGTH) {
      throw new KeyVerifyException(
          KeyType.SSK, "Data length wrong: " + data.length + " should be " + DATA_LENGTH);
    }

    var hashIdentifier = (short) (((headers[0] & 0xff) << 8) + (headers[1] & 0xff));
    if (hashIdentifier != HASH_SHA256) {
      throw new KeyVerifyException(KeyType.SSK, "Hash not SHA-256");
    }

    super(data, headers, nodeKey, hashIdentifier);

    publicKey = (DSAPublicKey) nodeKey.getPublicKey();

    if (publicKey == null) {
      throw new KeyVerifyException(KeyType.SSK, "PubKey was null from " + nodeKey);
    }

    int x = 2;
    symCipherIdentifier = (short) (((headers[x] & 0xff) << 8) + (headers[x + 1] & 0xff));
    x += 2;

    // Then E(H(docname))
    byte[] ehDocname = new byte[E_H_DOCNAME_LENGTH];
    System.arraycopy(headers, x, ehDocname, 0, ehDocname.length);
    x += E_H_DOCNAME_LENGTH;

    headersOffset = x; // is index to start of encrypted headers
    x += ENCRYPTED_HEADERS_LENGTH;

    // Extract the signature
    if (verify) {
      byte[] bufR = new byte[SIG_R_LENGTH];
      byte[] bufS = new byte[SIG_S_LENGTH];

      System.arraycopy(headers, x, bufR, 0, SIG_R_LENGTH);
      x += SIG_R_LENGTH;
      System.arraycopy(headers, x, bufS, 0, SIG_S_LENGTH);

      // Compute the hash on the data
      byte[] overallHash;
      MessageDigest md = Sha256.getMessageDigest();
      md.update(data);
      byte[] dataHash = md.digest();
      // All headers up to and not including the signature
      md.update(headers, 0, headersOffset + ENCRYPTED_HEADERS_LENGTH);
      // Then the implicit data hash
      md.update(dataHash);
      // Makes the implicit overall hash
      overallHash = md.digest();

      // Now verify it
      BigInteger r = new BigInteger(1, bufR);
      BigInteger s = new BigInteger(1, bufS);
      DSASigner dsa = new DSASigner();
      dsa.init(
          false, new DSAPublicKeyParameters(publicKey.getY(), Global.getDSAgroupBigAParameters()));

      // We probably don't need to try both here...
      // but that's what the legacy code was doing...
      // @see comments in Global before touching it
      if (!(dsa.verifySignature(Global.truncateHash(overallHash), r, s)
          || dsa.verifySignature(overallHash, r, s))) {
        throw new KeyVerifyException(
            KeyType.SSK, "Signature verification failed for node-level SSK");
      }
    }

    if (!Arrays.equals(ehDocname, nodeKey.getEhDocname())) {
      throw new KeyVerifyException(
          KeyType.SSK,
          "E(H(docname)) wrong - wrong key?? \nfrom headers: %s\nfrom key:     %s"
              .formatted(
                  HexUtil.bytesToHex(ehDocname), HexUtil.bytesToHex(nodeKey.getEhDocname())));
    }
  }

  @Override
  public byte[] getPubkeyBytes() {
    return publicKey.getEncoded();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NodeSskBlock that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    if (symCipherIdentifier != that.symCipherIdentifier
        || !Objects.equals(publicKey, that.publicKey)
        || headersOffset != that.headersOffset) {
      return false;
    }

    // only compare some of the headers (see top)
    for (int i = 0; i < HEADER_COMPARE_TO; i++) {
      if (that.getRawHeaders()[i] != getRawHeaders()[i]) return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), publicKey);
  }

  public int getHeadersOffset() {
    return headersOffset;
  }

  private final DSAPublicKey publicKey;

  /** The index of the first byte of encrypted fields in the headers, after E(H(docname)) */
  private final int headersOffset;

  private final short symCipherIdentifier;
}
