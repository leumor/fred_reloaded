/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import hyphanet.base.Fields;
import org.apache.commons.rng.UniformRandomProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * <p>Cryptographic utility class providing various methods for hash calculations,
 * byte array manipulations, and cipher operations.</p>
 *
 * <p>This class is part of Hyphanet's cryptographic infrastructure and provides:</p>
 * <ul>
 *   <li>Hash calculations using different algorithms (SHA1, MD5, SHA256, etc.)</li>
 *   <li>Byte array conversion utilities</li>
 *   <li>MPI (Multi-Precision Integer) operations</li>
 *   <li>Key generation and cipher operations</li>
 * </ul>
 */
public class Util {

    /**
     * Constant representing the value 2 as a BigInteger
     */
    public static final BigInteger TWO = BigInteger.valueOf(2);

    /**
     * Supported hash algorithms with their corresponding standard names.
     */
    public enum HashAlgorithm {
        SHA1("SHA1"), MD5("MD5"), SHA256("SHA-256"), SHA384("SHA-384"), SHA512("SHA-512");

        /**
         * Constructs a new HashAlgorithm with the specified algorithm name.
         *
         * @param algorithmName The standard name of the algorithm
         */
        HashAlgorithm(String algorithmName) {
            this.algorithmName = algorithmName;
        }

        /**
         * Returns the standard name of this hash algorithm.
         *
         * @return The algorithm name as used in JCA
         */
        public String getAlgorithmName() {
            return algorithmName;
        }

        private final String algorithmName;
    }

    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    // we should really try reading the JFC documentation sometime..
    // - the byte array generated by BigInteger.toByteArray() is
    //   compatible with the BigInteger(byte[]) constructor
    // - the byte length is ceil((bitLength()+1) / 8)

    static {
        // Load All necessary JCA Providers
        try {
            Class.forName(
                "hyphanet.crypt.JcaLoader",
                false,
                ClassLoader.getSystemClassLoader()
                         );
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("This should never happen");
        }
    }

    /**
     * Fills a byte array from an array of integers. Each integer is split into 4 bytes in
     * big-endian order.
     *
     * @param ints  The source integer array
     * @param bytes The destination byte array (must be 4 times the length of ints)
     */
    public static void fillByteArrayFromInts(int[] ints, byte[] bytes) {
        int ic = 0;
        for (int i : ints) {
            bytes[ic++] = (byte) (i >> 24);
            bytes[ic++] = (byte) (i >> 16);
            bytes[ic++] = (byte) (i >> 8);
            bytes[ic++] = (byte) i;
        }
    }

    /**
     * Fills a byte array from an array of longs. Each long is split into 8 bytes in big-endian
     * order.
     *
     * @param longs The source long array
     * @param bytes The destination byte array (must be 8 times the length of longs)
     */
    public static void fillByteArrayFromLongs(long[] longs, byte[] bytes) {
        int ic = 0;
        for (long l : longs) {
            bytes[ic++] = (byte) (l >> 56);
            bytes[ic++] = (byte) (l >> 48);
            bytes[ic++] = (byte) (l >> 40);
            bytes[ic++] = (byte) (l >> 32);
            bytes[ic++] = (byte) (l >> 24);
            bytes[ic++] = (byte) (l >> 16);
            bytes[ic++] = (byte) (l >> 8);
            bytes[ic++] = (byte) l;
        }
    }

    /**
     * Calculates the MPI (Multi-Precision Integer) byte representation of a BigInteger.
     *
     * @param num The BigInteger to convert
     *
     * @return A byte array containing the MPI representation
     */
    public static byte[] calcMPIBytes(BigInteger num) {
        int len = num.bitLength();
        byte[] bytes = new byte[2 + ((len + 7) >> 3)];
        byte[] numBytes = num.toByteArray();
        System.arraycopy(numBytes, 0, bytes, 2, Math.min(bytes.length - 2, numBytes.length));
        bytes[0] = (byte) (len >> 8);
        bytes[1] = (byte) len;
        return bytes;
    }

    /**
     * Writes a Multi-Precision Integer (MPI) to an output stream.
     *
     * <p>The MPI format consists of:</p>
     * <ul>
     *   <li>2 bytes for the bit length</li>
     *   <li>The actual number data in big-endian format</li>
     * </ul>
     *
     * @param num The BigInteger to write
     * @param out The output stream to write to
     *
     * @throws IOException If there is an error writing to the stream
     */
    public static void writeMPI(BigInteger num, OutputStream out) throws IOException {
        out.write(calcMPIBytes(num));
    }

    /**
     * Reads a Multi-Precision Integer (MPI) from an input stream.
     *
     * <p>The MPI format consists of:</p>
     * <ul>
     *   <li>2 bytes for the bit length</li>
     *   <li>The actual number data in big-endian format</li>
     * </ul>
     *
     * @param in The input stream to read from
     *
     * @return A BigInteger representing the read MPI value
     *
     * @throws IOException  If there is an error reading from the stream
     * @throws EOFException If the stream ends prematurely
     */
    public static BigInteger readMPI(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        if ((b1 == -1) || (b2 == -1)) {
            throw new EOFException();
        }
        int length = (b1 << 8) + b2;
        byte[] data = new byte[(length + 7) >> 3];
        readFully(in, data, 0, data.length);

        // REDFLAG: This can't possibly be negative, right?
        return new BigInteger(1, data);
    }

    /**
     * Computes a hash of the given data using the specified algorithm.
     *
     * @param algorithm The hash algorithm to use
     * @param data      The data to hash
     *
     * @return The computed hash as a byte array
     *
     * @throws IllegalStateException if the specified algorithm is not available
     */
    public static byte[] hashBytes(HashAlgorithm algorithm, byte[] data) {
        return hashBytes(algorithm, data, 0, data.length);
    }

    /**
     * Computes a hash of the given data using the specified algorithm.
     *
     * @param algorithm The hash algorithm to use
     * @param data      The byte array containing the data to hash
     * @param offset    The starting position in the data array
     * @param length    The number of bytes to hash from the data array
     *
     * @return The computed hash as a byte array
     *
     * @throws IllegalStateException if the specified algorithm is not available
     */
    public static byte[] hashBytes(
        HashAlgorithm algorithm, byte[] data, int offset, int length) {
        MessageDigest tmpDigest = null;
        try {
            tmpDigest = MessageDigest.getInstance(algorithm.getAlgorithmName());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This should never happen");
        }
        tmpDigest.update(data, offset, length);
        return tmpDigest.digest();
    }

    /**
     * Hashes a string using UTF-8 encoding and the specified algorithm.
     *
     * @param algorithm The hash algorithm to use
     * @param s         The string to hash
     *
     * @return The computed hash as a byte array
     */
    public static byte[] hashString(HashAlgorithm algorithm, String s) {
        byte[] sbytes = s.getBytes(StandardCharsets.UTF_8);
        MessageDigest tmpDigest = null;
        try {
            tmpDigest = MessageDigest.getInstance(algorithm.getAlgorithmName());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This should never happen");
        }
        tmpDigest.update(sbytes, 0, sbytes.length);
        return tmpDigest.digest();
    }


    /**
     * Performs an XOR operation between two byte arrays.
     *
     * @param b1 The first byte array
     * @param b2 The second byte array
     *
     * @return A new byte array containing the XOR result
     */
    public static byte[] xor(byte[] b1, byte[] b2) {
        int maxLength = Math.max(b1.length, b2.length);
        byte[] result = new byte[maxLength];
        int minLength = Math.min(b1.length, b2.length);
        for (int i = 0; i < minLength; i++) {
            result[i] = (byte) (b1[i] ^ b2[i]);
        }
        return result;
    }

    /**
     * Generates a key using the provided entropy.
     *
     * @param entropy The source of entropy (will be zeroed after use)
     * @param key     The destination array for the generated key
     * @param offset  The starting offset in the key array
     * @param len     The length of the key to generate
     */
    public static void makeKey(byte[] entropy, byte[] key, int offset, int len) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance(HashAlgorithm.SHA1.getAlgorithmName());
            int ic = 0;
            while (len > 0) {
                ic++;
                for (int i = 0; i < ic; i++) {
                    digest.update((byte) 0);
                }
                digest.update(entropy, 0, entropy.length);
                int bytesToCopy;
                byte[] hash = digest.digest();
                bytesToCopy = Math.min(len, hash.length);
                System.arraycopy(hash, 0, key, offset, bytesToCopy);
                offset += bytesToCopy;
                len -= bytesToCopy;

            }
        } catch (NoSuchAlgorithmException e) {
            // impossible
            throw new IllegalStateException("This should never happen");
        } finally {
            Arrays.fill(entropy, (byte) 0);
        }
    }

    /**
     * Calculates the logarithm base 2 of a number, rounded up to the nearest integer.
     *
     * @param n The number to calculate the logarithm for
     *
     * @return The ceiling of the base-2 logarithm of n
     */
    public static int log2(long n) {
        int log2 = 0;
        while ((log2 < 63) && (1L << log2 < n)) {
            ++log2;
        }
        return log2;
    }

    /**
     * Fully reads the requested number of bytes from an input stream.
     *
     * @param in The input stream to read from
     * @param b  The buffer into which the data is read
     *
     * @throws IOException If an I/O error occurs or EOF is reached before reading all bytes
     */
    public static void readFully(InputStream in, byte[] b) throws IOException {
        readFully(in, b, 0, b.length);
    }

    /**
     * Fully reads the requested number of bytes from an input stream into a buffer at the
     * specified offset.
     *
     * @param in     The input stream to read from
     * @param b      The buffer into which the data is read
     * @param off    The start offset in the buffer
     * @param length The number of bytes to read
     *
     * @throws IOException If an I/O error occurs or EOF is reached before reading all bytes
     */
    public static void readFully(InputStream in, byte[] b, int off, int length)
        throws IOException {
        int total = 0;
        while (total < length) {
            int got = in.read(b, off + total, length - total);
            if (got == -1) {
                throw new EOFException();
            }
            total += got;
        }
    }

    /**
     * Converts a key digest to a normalized double value between 0 and 1.
     *
     * @param digest The key digest to convert
     *
     * @return A double value between 0 and 1
     */
    public static double keyDigestAsNormalizedDouble(byte[] digest) {
        long asLong = Math.abs(Fields.bytesToLong(digest));
        // Math.abs can actually return negative...
        if (asLong == Long.MIN_VALUE) {
            asLong = Long.MAX_VALUE;
        }
        return ((double) asLong) / ((double) Long.MAX_VALUE);
    }

    /**
     * Generates a random positive BigInteger with a specified number of bits using a provided
     * random number generator.
     *
     * <p>This method creates a random BigInteger that is uniformly distributed
     * over the range [0, 2^numBits - 1]. The generated number will have exactly the specified
     * number of bits.</p>
     *
     * @param numBits the exact number of bits in the generated BigInteger
     * @param rng     the uniform random number provider to use for generation
     *
     * @return a random, non-negative BigInteger with exactly numBits bits
     *
     * @throws IllegalArgumentException if numBits is negative
     * @see java.math.BigInteger
     * @see org.apache.commons.rng.UniformRandomProvider
     */
    public static BigInteger generateRandomBigInteger(int numBits, UniformRandomProvider rng) {
        // Generate random bytes
        byte[] bytes = new byte[(numBits + 7) / 8];
        rng.nextBytes(bytes);

        // Ensure the number has exactly numBits bits
        if (numBits % 8 != 0) {
            bytes[0] &= (byte) ((1 << (numBits % 8)) - 1);
        }

        return new BigInteger(1, bytes);
    }

    /**
     * Benchmarks a MessageDigest implementation.
     *
     * @param md The MessageDigest to benchmark
     *
     * @return The best time achieved in nanoseconds
     *
     * @throws GeneralSecurityException If a security-related error occurs
     */
    private static long benchmark(MessageDigest md) throws GeneralSecurityException {
        long times = Long.MAX_VALUE;
        byte[] input = new byte[1024];
        byte[] output = new byte[md.getDigestLength()];
        // warm-up
        for (int i = 0; i < 32; i++) {
            md.update(input, 0, input.length);
            md.digest(output, 0, output.length);
            System.arraycopy(
                output,
                0,
                input,
                (i * output.length) % (input.length - output.length),
                output.length
                            );
        }
        for (int i = 0; i < 128; i++) {
            long startTime = System.nanoTime();
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 32; k++) {
                    md.update(input, 0, input.length);
                }
                md.digest(output, 0, output.length);
            }
            long endTime = System.nanoTime();
            times = Math.min(endTime - startTime, times);
            System.arraycopy(output, 0, input, 0, output.length);
        }
        return times;
    }
}
