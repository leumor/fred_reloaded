package hyphanet.access.block.client;

import hyphanet.access.KeyDecodeException;
import hyphanet.access.KeyType;
import hyphanet.access.KeyVerifyException;
import hyphanet.access.block.node.NodeChkBlock;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.client.ClientChk;
import hyphanet.access.key.node.NodeKey;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ClientChkBlock extends ClientKeyBlock<ClientChk, NodeChkBlock> {

  public ClientChkBlock(byte[] data, byte[] header, ClientChk clientChk, boolean verify)
      throws KeyVerifyException {
    var block =
        new NodeChkBlock(
            data, header, clientChk.getNodeKey(), clientChk.getCryptoAlgorithm(), verify);

    super(clientChk, block);
  }

  @Override
  public Bucket decode(BucketFactory factory, int maxLength, boolean dontDecompress)
      throws KeyDecodeException, IOException {

    if (getClientKey().getCryptoAlgorithm() != CryptoAlgorithm.ALGO_AES_CTR_256_SHA256) {
      throw new UnsupportedOperationException();
    }

    var cryptoKey = getClientKey().getCryptoKey();
    if (cryptoKey == null) {
      throw new KeyDecodeException(KeyType.CHK, "CryptoKey is null");
    }

    var headers = getBlock().getRawHeaders();
    var data = getBlock().getRawData();
    var hash = Arrays.copyOfRange(headers, 2, 2 + 32);
    var cryptoKeyBytes = cryptoKey.bytes();

    try {
      Cipher cipher = Cipher.getInstance("AES/CTR/NOPADDING");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(cryptoKeyBytes, "AES"),
          new IvParameterSpec(hash, 0, 16));
      byte[] plaintext = new byte[data.length + 2];
      int moved = cipher.update(data, 0, data.length, plaintext);
      cipher.doFinal(headers, hash.length + 2, 2, plaintext, moved);
      int size = ((plaintext[data.length] & 0xff) << 8) + (plaintext[data.length + 1] & 0xff);
      if (size > 32768) {
        throw new KeyDecodeException(KeyType.CHK, "Invalid size: " + size);
      }

      // Check the hash.
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(cryptoKeyBytes, "HmacSHA256"));
      hmac.update(plaintext); // plaintext includes lengthBytes
      byte[] hashCheck = hmac.doFinal();
      if (!Arrays.equals(hash, hashCheck)) {
        throw new KeyDecodeException(KeyType.CHK, "HMAC is wrong, wrong decryption key?");
      }

      return NodeKey.decompress(
          !dontDecompress && getClientKey().isCompressed(),
          plaintext,
          size,
          factory,
          maxLength,
          getClientKey().getCompressionAlgorithm(),
          false);
    } catch (GeneralSecurityException e) {
      throw new KeyDecodeException(KeyType.CHK, "Problem with JCA, should be impossible!", e);
    }
  }

  @Override
  public boolean isMetadata() {
    return false;
  }
}
