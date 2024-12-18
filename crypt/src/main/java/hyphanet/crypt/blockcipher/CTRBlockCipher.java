/**
 * Copied from Bouncycastle v147/SICBlockCipher.java. Unfortunately we can't use their JCE
 * without sorting out the policy files issues. Bouncycastle is MIT X licensed i.e. GPL
 * compatible.
 */
package hyphanet.crypt.blockcipher;

import hyphanet.crypt.BlockCipher;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the Segmented Integer Counter (SIC) mode on top of a simple block cipher. This
 * mode is also known as CTR (Counter) mode. In CTR mode, each block of plaintext is XORed with
 * an encrypted counter value. The counter is incremented for each block.
 *
 * <p>This implementation is thread-safe for concurrent encryption operations.</p>
 *
 * <p>The CTR mode has several advantages:</p>
 * <ul>
 *   <li>Parallel processing capability</li>
 *   <li>Random access to encrypted data</li>
 *   <li>No padding required</li>
 * </ul>
 *
 * @see BlockCipher
 */
public class CTRBlockCipher {
    /**
     * Constructs a new CTR mode block cipher.
     *
     * @param c the underlying block cipher implementation to be used
     */
    public CTRBlockCipher(BlockCipher c) {
        this.cipher = c;
        this.blockSize = cipher.getBlockSize() / 8;
        this.iv = new byte[blockSize];
        this.counter = new byte[blockSize];
        this.counterOut = new byte[blockSize];
        this.blockOffset = new AtomicInteger(iv.length);
    }


    /**
     * Returns the underlying block cipher implementation.
     *
     * @return the block cipher instance being wrapped
     */
    public BlockCipher getUnderlyingCipher() {
        return cipher;
    }


    /**
     * Initializes the cipher with an initialization vector (IV). Must only be called once for
     * any given IV.
     *
     * @param iv     the initialization vector array. This is the initial value of the
     *               plaintext counter. The plaintext is XORed with a sequence of bytes
     *               consisting of the encryption of successive values of the counter.
     * @param offset the starting position in the IV array
     * @param length the length of the IV to use
     *
     * @throws IllegalArgumentException if the IV length is incorrect or if offset/length are
     *                                  invalid
     */
    public synchronized void init(byte[] iv, int offset, int length) {
        if (length != this.iv.length) {
            throw new IllegalArgumentException("Invalid IV length");
        }

        if (offset < 0 || offset + length > iv.length) {
            throw new IllegalArgumentException("Invalid offset or length");
        }

        System.arraycopy(iv, offset, this.iv, 0, this.iv.length);
        System.arraycopy(this.iv, 0, counter, 0, counter.length);
        processBlock();
    }

    /**
     * Initializes the cipher with an initialization vector (IV). This method is a convenience
     * wrapper for the more detailed init method.
     *
     * @param iv the initialization vector array
     *
     * @throws IllegalArgumentException if the IV length is incorrect
     */
    public synchronized void init(byte[] iv) {
        init(iv, 0, iv.length);
    }

    /**
     * Returns the block size of the underlying cipher.
     *
     * @return the block size in bytes
     */
    public int getBlockSize() {
        return cipher.getBlockSize();
    }

    /**
     * Processes a single byte of data.
     *
     * @param in the input byte to process
     *
     * @return the processed (encrypted/decrypted) byte
     */
    public byte processByte(byte in) {
        int offset = blockOffset.get();
        if (offset == counterOut.length) {
            processBlock();
            offset = 0;
        }
        byte result = (byte) (in ^ counterOut[offset]);
        blockOffset.incrementAndGet();
        return result;
    }

    /**
     * Processes (encrypts/decrypts) a block of data.
     *
     * @param input     the input data array
     * @param offsetIn  the offset in the input array
     * @param length    the number of bytes to process
     * @param output    the output array for processed data
     * @param offsetOut the offset in the output array
     *
     * @throws IllegalArgumentException if any offset or length parameter is invalid
     */
    public void processBytes(
        byte[] input, int offsetIn, int length, byte[] output, int offsetOut) {
        // XOR the plaintext with counterOut until we run out of blockOffset,
        // then processBlock() to get a new counterOut.

        if (offsetIn < 0 || offsetOut < 0 || length < 0) {
            throw new IllegalArgumentException("Negative offset or length");
        }
        if (offsetIn + length > input.length || offsetOut + length > output.length) {
            throw new IllegalArgumentException("Length exceeds array bounds");
        }

        var currentOffset = blockOffset.get();
        if (currentOffset != 0) {
            /* handle first partially consumed block */
            int len = Math.min(blockSize - currentOffset, length);

            processPartialBlock(input, offsetIn, output, offsetOut, len);

            length -= len;
            offsetIn += len;
            offsetOut += len;

            if (length == 0) {
                return;
            }
            processBlock();
        }

        while (length > blockSize) {
            /* consume full blocks */
            // note: we skip *last* full block to avoid extra processBlock()
            processPartialBlock(input, offsetIn, output, offsetOut, blockSize);

            length -= blockSize;
            offsetIn += blockSize;
            offsetOut += blockSize;
            processBlock();
        }

        if (length > 0) {
            processPartialBlock(input, offsetIn, output, offsetOut, length);
        }
    }

    /**
     * Processes a partial block of data internally.
     *
     * @param input     the input data array
     * @param offsetIn  the offset in the input array
     * @param output    the output array for processed data
     * @param offsetOut the offset in the output array
     * @param length    the number of bytes to process
     */
    private void processPartialBlock(
        byte[] input, int offsetIn, byte[] output, int offsetOut, int length) {
        for (int i = 0; i < length; i++) {
            output[offsetOut + i] =
                (byte) (input[offsetIn + i] ^ counterOut[blockOffset.get()]);
            blockOffset.incrementAndGet();
        }
    }

    /**
     * Encrypts the counter value and increments it for the next block. This method is
     * synchronized to ensure thread safety.
     */
    private synchronized void processBlock() {
        // Our ciphers clobber the input array, so it is essential to copy
        // the counter to counterOut and then encrypt in-place.
        System.arraycopy(counter, 0, counterOut, 0, counter.length);
        cipher.encipher(counterOut, counterOut);

        // Now increment counter.
        for (int i = counter.length - 1; i >= 0; i--) {
            counter[i]++;
            if (counter[i] != 0) {
                break;
            }
        }

        blockOffset.set(0);
    }

    /**
     * The underlying block cipher implementation.
     */
    private final BlockCipher cipher;

    /**
     * Block size in bytes. Equal to IV length, counter length, and counterOut length.
     */
    private final int blockSize;

    /**
     * Initialization vector, equal to the initial value of the plaintext counter. This value
     * must be unique for each encryption operation with the same key.
     */
    private final byte[] iv;

    /**
     * The plaintext block counter. This is incremented (from last byte backwards) after each
     * block encryption.
     */
    private final byte[] counter;

    /**
     * The encrypted counter value. This is XORed with the plaintext to produce the
     * ciphertext.
     */
    private final byte[] counterOut;

    /**
     * Current offset within the current block. Used to track partial block processing.
     * Implemented as AtomicInteger for thread-safe operations.
     */
    private final AtomicInteger blockOffset;

}
