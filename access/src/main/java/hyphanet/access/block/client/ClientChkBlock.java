package hyphanet.access.block.client;

import hyphanet.access.KeyDecodeException;
import hyphanet.access.KeyEncodeException;
import hyphanet.access.KeyType;
import hyphanet.access.KeyVerifyException;
import hyphanet.access.block.node.NodeChkBlock;
import hyphanet.access.block.node.NodeKeyBlock;
import hyphanet.access.key.CompressionAlgorithm;
import hyphanet.access.key.CryptoAlgorithm;
import hyphanet.access.key.DecryptionKey;
import hyphanet.access.key.RoutingKey;
import hyphanet.access.key.client.ClientChk;
import hyphanet.access.key.node.NodeChk;
import hyphanet.access.key.node.NodeKey;
import hyphanet.crypt.Global;
import hyphanet.crypt.hash.Sha256;
import hyphanet.support.compress.InvalidCompressionCodecException;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

public class ClientChkBlock extends ClientKeyBlock<NodeChk, ClientChk, NodeChkBlock> {
  public static final int MAX_LENGTH_BEFORE_COMPRESSION = Integer.MAX_VALUE;
  public static final int DATA_LENGTH = 32768;

  public ClientChkBlock(NodeChkBlock block, ClientChk clientChk) throws KeyVerifyException {
    this(block.getRawData(), block.getRawHeaders(), clientChk, true);
  }

  public ClientChkBlock(byte[] data, byte[] header, ClientChk clientChk, boolean verify)
      throws KeyVerifyException {
    var block =
        new NodeChkBlock(
            data, header, clientChk.getNodeKey(), clientChk.getCryptoAlgorithm(), verify);

    super(clientChk, block);
  }

  /**
   * Format: [0-1]: Block hash algorithm [2-34]: HMAC (with cryptokey) of data + length bytes.
   * [35-36]: Length bytes. Encryption: CTR with IV = 1st 16 bytes of the hash. (It has to be
   * deterministic as this is a CHK and we need to be able to reinsert them easily): - Data - Length
   * bytes.
   *
   * @param data Data should already have been padded.
   * @param dataLength Length of original data. Between 0 and 32768.
   * @param md256 Convenient reuse of hash object.
   * @param encKey Encryption key for the data, part of the URI.
   * @param asMetadata Whether the final CHK is metadata or not.
   * @param compressionAlgorithm The compression algorithm used.
   * @param cryptoAlgorithm The encryption algorithm used.
   * @return
   */
  public static ClientChkBlock encode(
      byte[] data,
      int dataLength,
      MessageDigest md256,
      DecryptionKey encKey,
      boolean asMetadata,
      CompressionAlgorithm compressionAlgorithm,
      CryptoAlgorithm cryptoAlgorithm,
      int blockHashAlgorithm)
      throws KeyEncodeException {

    var transformation =
        switch (cryptoAlgorithm) {
          case ALGO_AES_PCFB_256_SHA256 -> "RIJNDAEL256/CFB/NoPadding";
          case ALGO_AES_CTR_256_SHA256 -> "AES/CTR/NoPadding";
        };

    var keyAgo =
        switch (cryptoAlgorithm) {
          case ALGO_AES_PCFB_256_SHA256 -> "Rijndael";
          case ALGO_AES_CTR_256_SHA256 -> "AES";
        };

    try {
      // IV = HMAC<cryptokey>(plaintext).
      // It's okay that this is the same for 2 blocks with the same key and the same content.
      // In fact that's the point; this is still a Content Hash Key.
      // FIXME And yes we should check on insert for multiple identical keys.
      Mac hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(encKey.getBytes(), "HmacSHA256"));
      byte[] tmpLen = new byte[] {(byte) (dataLength >> 8), (byte) (dataLength & 0xff)};
      hmac.update(data);
      hmac.update(tmpLen);
      byte[] hash = hmac.doFinal();

      var iv =
          switch (cryptoAlgorithm) {
            case ALGO_AES_PCFB_256_SHA256 -> new IvParameterSpec(hash, 0, 32);
            case ALGO_AES_CTR_256_SHA256 -> new IvParameterSpec(hash, 0, 16);
          };

      byte[] header = new byte[hash.length + 2 + 2];
      if (blockHashAlgorithm == 0) blockHashAlgorithm = NodeKeyBlock.HASH_SHA256;
      if (blockHashAlgorithm != NodeKeyBlock.HASH_SHA256)
        throw new IllegalArgumentException("Unsupported block hash algorithm " + cryptoAlgorithm);
      header[0] = (byte) 0;
      header[1] = (byte) (blockHashAlgorithm & 0xff);
      System.arraycopy(hash, 0, header, 2, hash.length);

      SecretKey ckey = new SecretKeySpec(encKey.getBytes(), keyAgo);
      // CTR mode IV is only 16 bytes.
      // That's still plenty though. It will still be unique.
      Cipher cipher = Cipher.getInstance(transformation);
      cipher.init(Cipher.ENCRYPT_MODE, ckey, iv);

      byte[] cdata = new byte[data.length];
      int moved = cipher.doFinal(data, 0, data.length, cdata);
      if (moved == data.length) {
        cipher.doFinal(tmpLen, 0, 2, header, hash.length + 2);
      } else {
        // FIXME inefficient
        byte[] tmp = cipher.doFinal(tmpLen, 0, 2);
        System.arraycopy(tmp, 0, cdata, moved, tmp.length - 2);
        System.arraycopy(tmp, tmp.length - 2, header, hash.length + 2, 2);
      }

      // Now calculate the final hash
      md256.update(header);
      byte[] finalHash = md256.digest(cdata);

      // Now convert it into a ClientCHK
      var finalKey =
          new ClientChk(
              new RoutingKey(finalHash), encKey, cryptoAlgorithm, asMetadata, compressionAlgorithm);

      try {
        return new ClientChkBlock(cdata, header, finalKey, false);
      } catch (KeyVerifyException e3) {
        // WTF?
        throw new KeyEncodeException(KeyType.CHK, e3);
      }
    } catch (GeneralSecurityException e) {
      throw new KeyEncodeException(KeyType.CHK, "Problem with JCA, should be impossible!", e);
    }
  }

  /**
   * Encode a Bucket of data to a CHKBlock.
   *
   * @param sourceData The bucket of data to encode. Can be arbitrarily large.
   * @param asMetadata Is this a metadata key?
   * @param dontCompress If set, don't even try to compress.
   * @param precompressedAlgo If !dontCompress, and this is >=0, then the data is already
   *     compressed, and this is the algorithm.
   * @param compressorDescriptor
   * @param cryptoAlgorithm
   * @param cryptoKey
   * @throws KeyEncodeException
   * @throws IOException If there is an error reading from the Bucket.
   * @throws InvalidCompressionCodecException
   */
  public static ClientChkBlock encode(
      Bucket sourceData,
      boolean asMetadata,
      boolean dontCompress,
      CompressionAlgorithm precompressedAlgo,
      long sourceLength,
      String compressorDescriptor,
      @Nullable DecryptionKey cryptoKey,
      CryptoAlgorithm cryptoAlgorithm)
      throws KeyEncodeException, IOException {
    byte[] finalData = null;
    byte[] data;
    CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NO_COMP;
    try {
      NodeKey.Compressed comp =
          NodeKey.compress(
              sourceData,
              dontCompress,
              precompressedAlgo,
              sourceLength,
              MAX_LENGTH_BEFORE_COMPRESSION,
              DATA_LENGTH,
              false,
              compressorDescriptor);
      finalData = comp.compressedData();
      compressionAlgorithm = comp.compressionAlgorithm();
    } catch (KeyEncodeException | InvalidCompressionCodecException e) {
      throw new KeyEncodeException(KeyType.CHK, e.getMessage(), e);
    }

    // Now do the actual encode
    // First pad it
    int dataLength = finalData.length;
    if (finalData.length != DATA_LENGTH) {
      data = new byte[DATA_LENGTH];
      Global.SECURE_RANDOM.nextBytes(data);
    } else {
      data = finalData;
    }

    // Now make the header
    MessageDigest md256 = Sha256.getMessageDigest();

    DecryptionKey encKey;
    if (cryptoKey != null) encKey = cryptoKey;
    else encKey = new DecryptionKey(md256.digest(data));

    return encode(
        data,
        dataLength,
        md256,
        encKey,
        asMetadata,
        compressionAlgorithm,
        cryptoAlgorithm,
        NodeKeyBlock.HASH_SHA256);
  }

  /**
   * Encode a splitfile block.
   *
   * @param data The data to encode. Must be exactly DATA_LENGTH bytes.
   * @param cryptoKey The encryption key. Can be null in which case this is equivalent to a normal
   *     block encode.
   */
  public static ClientChkBlock encodeSplitfileBlock(
      byte[] data, DecryptionKey cryptoKey, CryptoAlgorithm cryptoAlgorithm)
      throws KeyEncodeException {
    if (data.length != DATA_LENGTH) throw new IllegalArgumentException();
    if (cryptoKey != null && cryptoKey.getBytes().length != 32)
      throw new IllegalArgumentException();
    MessageDigest md256 = Sha256.getMessageDigest();
    // No need to pad
    if (cryptoKey == null) {
      cryptoKey = new DecryptionKey(md256.digest(data));
    }
    return encode(
        data,
        DATA_LENGTH,
        md256,
        cryptoKey,
        false,
        CompressionAlgorithm.NO_COMP,
        cryptoAlgorithm,
        NodeKeyBlock.HASH_SHA256);
  }

  @Override
  public Bucket decode(BucketFactory factory, int maxLength, boolean dontDecompress)
      throws KeyDecodeException, IOException {

    var transformation =
        switch (getClientKey().getCryptoAlgorithm()) {
          case ALGO_AES_PCFB_256_SHA256 -> "RIJNDAEL256/CFB/NoPadding";
          case ALGO_AES_CTR_256_SHA256 -> "AES/CTR/NoPadding";
        };

    var keyAgo =
        switch (getClientKey().getCryptoAlgorithm()) {
          case ALGO_AES_PCFB_256_SHA256 -> "Rijndael";
          case ALGO_AES_CTR_256_SHA256 -> "AES";
        };

    var cryptoKey = getClientKey().getDecryptionKey();
    if (cryptoKey == null) {
      throw new KeyDecodeException(KeyType.CHK, "CryptoKey is null");
    }

    var headers = getBlock().getRawHeaders();
    var data = getBlock().getRawData();
    var hash = Arrays.copyOfRange(headers, 2, 2 + 32);
    var cryptoKeyBytes = cryptoKey.getBytes();

    var iv =
        switch (getClientKey().getCryptoAlgorithm()) {
          case ALGO_AES_PCFB_256_SHA256 -> new IvParameterSpec(hash, 0, 32);
          case ALGO_AES_CTR_256_SHA256 -> new IvParameterSpec(hash, 0, 16);
        };

    try {
      Cipher cipher = Cipher.getInstance(transformation);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cryptoKeyBytes, keyAgo), iv);
      byte[] plaintext;
      byte[] plainData = cipher.doFinal(data, 0, data.length);
      byte[] plainHeader = cipher.doFinal(headers, hash.length + 2, 2);
      int size = ((plainHeader[0] & 0xff) << 8) + (plainHeader[1] & 0xff);
      if (size > 32768) {
        throw new KeyDecodeException(KeyType.CHK, "Invalid size: " + size);
      }

      plaintext = ArrayUtils.addAll(plainData, plainHeader[0], plainHeader[1]);

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
