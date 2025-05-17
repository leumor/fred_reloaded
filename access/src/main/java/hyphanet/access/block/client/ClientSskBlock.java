package hyphanet.access.block.client;

import hyphanet.access.KeyDecodeException;
import hyphanet.access.KeyType;
import hyphanet.access.KeyVerifyException;
import hyphanet.access.block.node.NodeSskBlock;
import hyphanet.access.key.CompressionAlgorithm;
import hyphanet.access.key.client.ClientSsk;
import hyphanet.access.key.node.NodeKey;
import hyphanet.access.key.node.NodeSsk;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import hyphanet.support.io.storage.bucket.BucketTools;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static hyphanet.access.block.node.NodeSskBlock.DATA_DECRYPT_KEY_LENGTH;

public class ClientSskBlock extends ClientKeyBlock<NodeSsk, ClientSsk, NodeSskBlock> {
  private static final int MAX_DECOMPRESSED_DATA_LENGTH = 32768;

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ClientSskBlock.class);

  public ClientSskBlock(byte[] data, byte[] header, ClientSsk clientSsk, boolean verify)
      throws KeyVerifyException {
    var block = new NodeSskBlock(data, header, clientSsk.getNodeKey(), verify);

    super(clientSsk, block);
  }

  @Override
  public Bucket decode(BucketFactory factory, int maxLength, boolean dontDecompress)
      throws KeyDecodeException, IOException {
    /* We know the signature is valid because it is checked in the constructor. */
    /* We also know e(h(docname)) is valid */
    byte[] decryptedHeaders = new byte[NodeSskBlock.ENCRYPTED_HEADERS_LENGTH];
    System.arraycopy(
        getBlock().getRawHeaders(),
        getBlock().getHeadersOffset(),
        decryptedHeaders,
        0,
        NodeSskBlock.ENCRYPTED_HEADERS_LENGTH);

    var clientKey = getClientKey();

    assert clientKey.getDecryptionKey() != null;
    SecretKeySpec k = new SecretKeySpec(clientKey.getDecryptionKey().getBytes(), "Rijndael");
    // ECB-encrypted E(H(docname)) serves as IV.
    var params = new IvParameterSpec(clientKey.getEhDocname());

    Cipher c;
    try {
      c = Cipher.getInstance("RIJNDAEL256/CFB/NoPadding");
      c.init(Cipher.DECRYPT_MODE, k, params);
    } catch (NoSuchPaddingException
        | NoSuchAlgorithmException
        | InvalidKeyException
        | InvalidAlgorithmParameterException e) {
      throw new IllegalStateException("Problem with JCA, should be impossible!", e);
    }

    try {
      decryptedHeaders = c.doFinal(decryptedHeaders);
    } catch (IllegalBlockSizeException | BadPaddingException e) {
      throw new KeyDecodeException(KeyType.SSK, e);
    }

    // First 32 bytes are the key
    byte[] dataDecryptKey = Arrays.copyOf(decryptedHeaders, DATA_DECRYPT_KEY_LENGTH);

    k = new SecretKeySpec(dataDecryptKey, "Rijndael");
    // Data decrypt key should be unique, so use it as IV
    params = new IvParameterSpec(dataDecryptKey);

    byte[] dataOutput;
    try {
      c.init(Cipher.DECRYPT_MODE, k, params);
      dataOutput = c.doFinal(getBlock().getRawData());
    } catch (InvalidKeyException
        | InvalidAlgorithmParameterException
        | IllegalBlockSizeException
        | BadPaddingException e) {
      throw new KeyDecodeException(KeyType.SSK, e);
    }

    // 2 bytes - data length
    int dataLength =
        ((decryptedHeaders[DATA_DECRYPT_KEY_LENGTH] & 0xff) << 8)
            + (decryptedHeaders[DATA_DECRYPT_KEY_LENGTH + 1] & 0xff);
    // Metadata flag is top bit
    if ((dataLength & 32768) != 0) {
      dataLength = dataLength & ~32768;
      isMetadata = true;
    }
    if (dataLength > dataOutput.length) {
      throw new KeyDecodeException(
          KeyType.SSK,
          "Data length: %d but data.length=%d".formatted(dataLength, dataOutput.length));
    }

    compressionAlgorithm =
        CompressionAlgorithm.fromValue(
            (short)
                (((decryptedHeaders[DATA_DECRYPT_KEY_LENGTH + 2] & 0xff) << 8)
                    + (decryptedHeaders[DATA_DECRYPT_KEY_LENGTH + 3] & 0xff)));
    decoded = true;

    if (dontDecompress) {
      if (compressionAlgorithm == CompressionAlgorithm.NO_COMP)
        return BucketTools.makeImmutableBucket(factory, dataOutput, dataLength);
      else if (dataLength < 2)
        throw new KeyDecodeException(KeyType.SSK, "Data length is less than 2 yet compressed!");
      else return BucketTools.makeImmutableBucket(factory, dataOutput, 2, dataLength - 2);
    }

    return NodeKey.decompress(
        compressionAlgorithm.getValue() >= 0,
        dataOutput,
        dataLength,
        factory,
        Math.min(MAX_DECOMPRESSED_DATA_LENGTH, maxLength),
        compressionAlgorithm,
        true);
  }

  @Override
  public boolean isMetadata() {
    return false;
  }

  /** Has decoded? */
  private boolean decoded;

  /** Is metadata. Set on decode. */
  private boolean isMetadata;

  private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NO_COMP;
}
