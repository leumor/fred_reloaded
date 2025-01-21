package hyphanet.crypt.io;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.OCBBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * An {@link OutputStream} that encrypts and authenticates data using Authenticated Encryption
 * with Associated Data (AEAD) in OCB (Offset Codebook) mode, typically with AES ciphers. This
 * class writes a unique nonce to the stream upon initialization, which is required for
 * decryption.
 * <p>
 * The implementation uses Bouncy Castle's {@link AEADBlockCipher} for encryption and
 * authentication. Data is processed in blocks, and the final MAC (Message Authentication Code)
 * is appended upon closing the stream. The overhead per encrypted block includes the AES block
 * size and MAC size.
 *
 * @author toad
 */
public class AeadOutputStream extends FilterOutputStream {

    /** MAC size in bits (128 bits for OCB-AES). */
    static final int MAC_SIZE_BITS = 128;

    /**
     * MAC size in bytes (16 bytes).
     */
    static final int MAC_SIZE_BYTES = MAC_SIZE_BITS / 8;

    /**
     * AES block size in bytes (16 bytes).
     */
    static final int AES_BLOCK_SIZE = 16;

    /**
     * Total encryption overhead per block: AES block size + MAC size (32 bytes).
     */
    public static final int AES_OVERHEAD = AES_BLOCK_SIZE + MAC_SIZE_BYTES;

    /**
     * Constructs an AEAD encrypting stream. Will write the nonce to the stream.
     *
     * @param os         Underlying output stream to write encrypted data and nonce.
     * @param key        Encryption key (must match cipher requirements, e.g., 16/24/32 bytes
     *                   for AES).
     * @param nonce      Nonce (IV) for encryption. Must be unique and have the top bit of the
     *                   first byte set to 0. We will write it to the stream so the other side
     *                   can pick it up, like an IV. Should generally be generated from a
     *                   SecureRandom.
     * @param hashCipher Block cipher used for MAC computation, not a block mode (e.g.,
     *                   {@link AESLightEngine}). This will not be used very much.
     * @param mainCipher Block cipher used for data encryption, not a block mode (e.g.,
     *                   {@link AESEngine}). This will be used for encrypting a fairly large
     *                   amount of data so could be any of the 3 BC AES impl's.
     *
     * @throws IOException If writing the nonce to the stream fails.
     */

    public AeadOutputStream(
        OutputStream os,
        byte[] key,
        byte[] nonce,
        BlockCipher hashCipher,
        BlockCipher mainCipher
    ) throws IOException {
        super(os);
        os.write(nonce);
        cipher = new OCBBlockCipher(hashCipher, mainCipher);
        KeyParameter keyParam = new KeyParameter(key);
        AEADParameters params = new AEADParameters(keyParam, MAC_SIZE_BITS, nonce);
        cipher.init(true, params);
    }

    /**
     * Factory method to create an AES-OCB encrypted stream with a randomly generated nonce.
     *
     * @param os     Underlying output stream.
     * @param key    AES encryption key (16/24/32 bytes).
     * @param random Secure random generator for nonce creation.
     *
     * @return Configured AEAD stream instance.
     *
     * @throws IOException If nonce generation or writing fails.
     */
    public static AeadOutputStream createAES(OutputStream os, byte[] key, SecureRandom random)
        throws IOException {
        return innerCreateAes(os, key, random);
    }

    /**
     * Encrypts a single byte and writes it to the stream. Internally buffers the byte and
     * delegates to {@link #write(byte[], int, int)}.
     */
    @Override
    public void write(int b) throws IOException {
        oneByte[0] = (byte) b;
        write(oneByte, 0, 1);
    }

    @Override
    public void write(byte[] buf) throws IOException {
        write(buf, 0, buf.length);
    }

    /**
     * Encrypts and writes a subset of a byte array to the stream.
     * <p>
     * Data is processed through the AEAD cipher, producing encrypted bytes and intermediate
     * MAC values. The encrypted bytes are written immediately, but the final MAC is only
     * appended upon calling {@link #close()}.
     *
     * @param buf    Source buffer.
     * @param offset Starting offset in the buffer.
     * @param length Number of bytes to process.
     */
    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buf.length);

        byte[] output = new byte[cipher.getUpdateOutputSize(length)];
        int processed = cipher.processBytes(buf, offset, length, output, 0);
        if (processed > 0) {
            out.write(output, 0, processed);
        }
    }

    /**
     * Finalizes encryption, appends the final MAC to the stream, and closes the underlying
     * stream. After this, no more data can be written.
     *
     * @throws IOException           If closing the underlying stream fails.
     * @throws IllegalStateException If cipher finalization fails (should never happen in
     *                               encryption).
     */
    @Override
    public void close() throws IOException {
        byte[] output = new byte[cipher.getOutputSize(0)];
        try {
            int processed = cipher.doFinal(output, 0);
            out.write(output, 0, processed);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("Impossible: " + e);
        }
        out.close();
    }

    @Override
    public String toString() {
        return "AeadOutputStream:" + out;
    }

    /**
     * For unit tests only: Creates an instance with configurable Random (not necessarily
     * secure).
     */
    static AeadOutputStream innerCreateAes(OutputStream os, byte[] key, Random random)
        throws IOException {
        var mainCipher = AESEngine.newInstance();
        AESLightEngine hashCipher = new AESLightEngine();
        byte[] nonce = new byte[mainCipher.getBlockSize()];
        random.nextBytes(nonce);
        nonce[0] &= 0x7F;
        return new AeadOutputStream(os, key, nonce, hashCipher, mainCipher);
    }

    /**
     * AEAD cipher instance for encryption and authentication.
     */
    private final AEADBlockCipher cipher;

    /**
     * Temporary buffer for single-byte writes.
     */
    private final byte[] oneByte = new byte[1];

}
