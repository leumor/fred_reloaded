package hyphanet.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.BitSet;

/**
 * Utility class for hexadecimal operations and conversions.
 * <p>
 * This class provides methods for:
 * <ul>
 *   <li>Converting between byte arrays and hexadecimal strings</li>
 *   <li>Manipulating BitSets with hexadecimal representations</li>
 *   <li>Handling BigInteger conversions to/from hex format</li>
 *   <li>Reading/writing BigIntegers to data streams</li>
 * </ul>
 * </p>
 * <p>
 * Unless otherwise stated, the conventions follow the rules outlined in the
 * Java Language Specification for hexadecimal notation.
 * </p>
 *
 * @author syoung
 */
public class HexUtil {
    /**
     * Logger for this class. Used for debugging purposes, particularly in bit manipulation methods.
     */
    private static final Logger logger = LoggerFactory.getLogger(HexUtil.class);

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private HexUtil() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Converts a byte array into a string of lower case hex characters.
     *
     * @param bs     the byte array to convert
     * @param off    the index of the first byte to read
     * @param length the number of bytes to read
     *
     * @return a string of hexadecimal characters
     *
     * @throws IllegalArgumentException if the input parameters are invalid
     */
    public static String bytesToHex(byte[] bs, int off, int length) {
        if (bs.length < off + length) {
            throw new IllegalArgumentException(
                String.format("Total length: %d, offset: %d, requested length: %d", bs.length,
                              off, length));
        }
        StringBuilder sb = new StringBuilder(length * 2);
        bytesToHexAppend(bs, off, length, sb);
        return sb.toString();
    }

    /**
     * Appends the hexadecimal representation of bytes to a StringBuilder.
     *
     * @param bs     the byte array to convert
     * @param off    the starting offset in the byte array
     * @param length the number of bytes to convert
     * @param sb     the StringBuilder to append to
     *
     * @throws IllegalArgumentException if the input parameters are invalid
     */
    public static void bytesToHexAppend(byte[] bs, int off, int length, StringBuilder sb) {
        if (bs.length < off + length) {
            throw new IllegalArgumentException("Invalid array bounds");
        }
        sb.ensureCapacity(sb.length() + length * 2);
        for (int i = off; i < (off + length); i++) {
            sb.append(Character.forDigit((bs[i] >>> 4) & 0xf, 16));
            sb.append(Character.forDigit(bs[i] & 0xf, 16));
        }
    }

    /**
     * Converts an entire byte array to a hexadecimal string.
     *
     * @param bs the byte array to convert
     *
     * @return the hexadecimal string representation
     */
    public static String bytesToHex(byte[] bs) {
        return bytesToHex(bs, 0, bs.length);
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * @param s the hexadecimal string to convert
     *
     * @return the resulting byte array
     *
     * @throws NumberFormatException if the string contains invalid hex characters
     */
    public static byte[] hexToBytes(String s) {
        return hexToBytes(s, 0);
    }

    /**
     * Converts a hexadecimal string to a byte array, storing the result at a specified offset
     * within the output array.
     * <p>
     * If the input hexadecimal string {@code s} has an odd number of characters, a leading '0' is
     * implicitly prepended to it before conversion. For example, "ABC" would be treated as "0ABC".
     * Each pair of hexadecimal characters is converted into a single byte.
     * </p>
     * <p>
     * The output byte array {@code bs} will be allocated with a size of {@code off + (1 + s.length()) / 2}.
     * The converted bytes from the hexadecimal string will be placed starting at {@code bs[off]}.
     * </p>
     *
     * @param s   the hexadecimal string to convert. Can be of odd or even length.
     *            Allowed characters are '0'-'9', 'a'-'f', 'A'-'F'.
     * @param off the starting offset in the resulting byte array where the converted bytes will be stored.
     *            This offset determines the number of leading zero bytes or pre-existing data before
     *            the converted hex data.
     * @return a new byte array of size {@code off + (s.length()+1)/2}, containing the converted
     *         hexadecimal values starting at the specified offset.
     * @throws NumberFormatException if the string {@code s} contains any characters other than valid
     *                               hexadecimal digits.
     * @see #hexToBytes(String, byte[], int)
     */
    public static byte[] hexToBytes(String s, int off) {
        // Calculate needed length: `off` for prefix, `(s.length()+1)/2` for hex data.
        // (s.length()+1)/2 correctly handles both odd and even s.length().
        byte[] bs = new byte[off + (s.length() + 1) / 2];
        hexToBytes(s, bs, off); // hexToBytes(String, byte[], int) handles odd length by prepending '0'
        return bs;
    }

    /**
     * Converts a String of hex characters into an array of bytes.
     *
     * @param s   a string of hex characters (upper case or lower) of even length
     * @param out a byte array of length at least s.length()/2 + off
     * @param off the first byte to write of the array
     *
     * @throws NumberFormatException     if the string contains invalid hex characters
     * @throws IndexOutOfBoundsException if the output buffer is too small
     */
    public static void hexToBytes(String s, byte[] out, int off)
        throws NumberFormatException, IndexOutOfBoundsException {

        int slen = s.length();
        if ((slen % 2) != 0) {
            s = '0' + s;
        }

        if (out.length < off + slen / 2) {
            throw new IndexOutOfBoundsException(
                "Output buffer too small for input (" + out.length + '<' + off + slen / 2 +
                ')');
        }

        // Safe to assume the string is even length
        byte b1, b2;
        for (int i = 0; i < slen; i += 2) {
            b1 = (byte) Character.digit(s.charAt(i), 16);
            b2 = (byte) Character.digit(s.charAt(i + 1), 16);
            if ((b1 < 0) || (b2 < 0)) {
                throw new NumberFormatException();
            }
            out[off + i / 2] = (byte) (b1 << 4 | b2);
        }
    }

    /**
     * Converts a BitSet to a byte array.
     * <p>
     * Packs the bits in the BitSet into a byte array, considering the specified size starting
     * from the least significant bit (LSB).
     * </p>
     *
     * @param ba   the BitSet to convert
     * @param size the number of bits to consider from LSB
     *
     * @return byte array containing the packed bits
     *
     * @throws IllegalStateException if internal calculation exceeds byte range
     */
    public static byte[] bitsToBytes(BitSet ba, int size) {
        int bytesAlloc = countBytesForBits(size);
        byte[] b = new byte[bytesAlloc];
        StringBuilder sb = null;

        if (logger.isDebugEnabled()) {
            // "8 * bytesAlloc" would only allocate enough space for the number of bits
            // "2 * 8 * bytesAlloc" correctly allocates space for the string representation
            // where each
            // bit becomes a character
            sb = new StringBuilder(2 * 8 * bytesAlloc);
        }

        for (int i = 0; i < b.length; i++) {
            short s = 0;
            for (int j = 0; j < 8; j++) {
                int idx = i * 8 + j;
                boolean val = idx <= size - 1 && ba.get(idx);
                s |= (short) (val ? (1 << j) : 0);
                if (sb != null) {
                    sb.append(val ? '1' : '0');
                }
            }
            if (s > 255) {
                throw new IllegalStateException("Value overflow: " + s);
            }
            b[i] = (byte) s;
        }
        if (sb != null) {
            logger.debug("bytes: {} returned from bitsToBytes({},{}): {} for {}", bytesAlloc,
                         ba, size, bytesToHex(b), sb);
        }
        return b;
    }

    /**
     * Converts a BitSet to a hexadecimal string representation.
     *
     * @param ba   the BitSet to convert
     * @param size the number of bits to consider
     *
     * @return hexadecimal string representation of the BitSet
     */
    public static String bitsToHexString(BitSet ba, int size) {
        return bytesToHex(bitsToBytes(ba, size));
    }

    /**
     * Converts a BigInteger to a hexadecimal string.
     *
     * @param i the BigInteger to convert
     *
     * @return hexadecimal string representation
     *
     * @throws NullPointerException if the input is null
     */
    public static String toHexString(BigInteger i) {
        return bytesToHex(i.toByteArray());
    }

    /**
     * Calculates the number of bytes required to represent a given number of bits.
     *
     * @param size the number of bits
     *
     * @return the number of bytes needed
     *
     * @throws IllegalArgumentException if size is negative
     */
    public static int countBytesForBits(int size) {
        return (size + 7) / 8;
    }

    /**
     * Reads bits from a byte array and sets them in a {@link BitSet}.
     * Bits are read from the first byte in the array, proceeding from the least significant bit (LSB)
     * to the most significant bit (MSB) within each byte, and then moving to the next byte.
     *
     * <p>For example, if {@code b = {0x01, 0x02}} and {@code numBitsToRead = 10}:
     * <ul>
     *   <li>Byte 0 (0x01 = 00000001 in binary):
     *     <ul>
     *       <li>BitSet index 0 gets bit 0 of byte 0 (1)</li>
     *       <li>BitSet index 1 gets bit 1 of byte 0 (0)</li>
     *       <li>...</li>
     *       <li>BitSet index 7 gets bit 7 of byte 0 (0)</li>
     *     </ul>
     *   </li>
     *   <li>Byte 1 (0x02 = 00000010 in binary):
     *     <ul>
     *       <li>BitSet index 8 gets bit 0 of byte 1 (0)</li>
     *       <li>BitSet index 9 gets bit 1 of byte 1 (1)</li>
     *     </ul>
     *   </li>
     * </ul>
     * Only {@code numBitsToRead} bits will be set in the {@code BitSet}. If the byte array
     * {@code b} contains fewer than {@code numBitsToRead} bits, all available bits from {@code b}
     * will be read and set.
     *
     * @param b              the byte array to read from.
     * @param bitSetTarget   the {@link BitSet} to write the bits to. Any existing bits may be overwritten.
     * @param numBitsToRead  the total number of bits to read from the byte array and set in the
     *                       {@code BitSet}. Bits will be set in {@code bitSetTarget} from index 0
     *                       up to {@code numBitsToRead - 1}.
     */
    public static void bytesToBits(byte[] b, BitSet bitSetTarget, int numBitsToRead) {
        if (logger.isDebugEnabled()) {
            logger.debug("bytesToBits(bytesToHex(b)={}, bitSetTarget, numBitsToRead={})", bytesToHex(b), numBitsToRead);
        }

        int bitSetIndex = 0;
        for (byte currentByte : b) {
            for (int bitInByteIndex = 0; bitInByteIndex < 8; bitInByteIndex++) {
                if (bitSetIndex >= numBitsToRead) {
                    // All requested bits have been read.
                    return;
                }
                int mask = 1 << bitInByteIndex;
                boolean value = (mask & currentByte) != 0;
                bitSetTarget.set(bitSetIndex, value);
                bitSetIndex++;
            }
        }
    }

    /**
     * Converts a hexadecimal string to a {@link BitSet}.
     * The hexadecimal string is first converted to a byte array, and then {@link #bytesToBits(byte[], BitSet, int)}
     * is used to populate the {@code BitSet}.
     *
     * @param hexString      the hexadecimal string representing the bits. If the string has an odd length,
     *                       a leading '0' is implicitly prepended.
     * @param bitSetTarget   the {@link BitSet} to store the converted bits in.
     * @param numBitsToSet   the total number of bits to take from the converted hex string and set in the
     *                       {@code BitSet}. Bits will be set in {@code bitSetTarget} from index 0
     *                       up to {@code numBitsToSet - 1}. If the hex string represents fewer
     *                       bits than {@code numBitsToSet}, all bits from the hex string will be set.
     * @throws NumberFormatException if {@code hexString} contains non-hexadecimal characters.
     * @see #hexToBytes(String)
     * @see #bytesToBits(byte[], BitSet, int)
     */
    public static void hexToBits(String hexString, BitSet bitSetTarget, int numBitsToSet) {
        byte[] b = hexToBytes(hexString); // Handles odd length strings by padding.
        bytesToBits(b, bitSetTarget, numBitsToSet);
    }

    /**
     * Writes a non-negative {@link BigInteger} to a {@link DataOutputStream}.
     * The format used is:
     * <ol>
     *   <li>The length of the BigInteger's byte array representation (obtained from
     *       {@link BigInteger#toByteArray()}) is written as a {@code short} (2 bytes).</li>
     *   <li>The byte array itself is then written to the stream.</li>
     * </ol>
     * This method is suitable for serializing non-negative BigIntegers. The maximum size of the
     * BigInteger (in terms of its byte array representation) is limited by {@link Short#MAX_VALUE}.
     *
     * @param integer the {@link BigInteger} to write. Must be non-negative.
     * @param out     the {@link DataOutputStream} to write to.
     * @throws IOException           if an I/O error occurs while writing to the stream.
     * @throws IllegalStateException if the {@code integer} is negative, or if its byte array
     *                               representation is longer than {@link Short#MAX_VALUE}.
     * @see BigInteger#toByteArray()
     * @see #readBigInteger(DataInputStream)
     */
    public static void writeBigInteger(BigInteger integer, DataOutputStream out)
        throws IOException {
        if (integer.signum() == -1) {
            throw new IllegalStateException("Negative BigInteger not allowed. Value: " + integer);
        }

        byte[] buf = integer.toByteArray(); // Minimal two's-complement representation.
        if (buf.length > Short.MAX_VALUE) {
            throw new IllegalStateException(
                String.format("BigInteger too long: %d bytes. Maximum allowed is %d.",
                              buf.length, Short.MAX_VALUE));
        }

        out.writeShort((short) buf.length);
        out.write(buf);
    }

    /**
     * Reads a {@link BigInteger} from a {@link DataInputStream} that was written using the
     * format defined in {@link #writeBigInteger(BigInteger, DataOutputStream)}.
     * <p>
     * The format expected is:
     * <ol>
     *   <li>A {@code short} indicating the length of the byte array for the BigInteger.</li>
     *   <li>The byte array itself.</li>
     * </ol>
     * The method reconstructs the {@link BigInteger} assuming it represents a positive value
     * (using {@code new BigInteger(1, buf)}). This is consistent with
     * {@link #writeBigInteger(BigInteger, DataOutputStream)} which only allows non-negative integers.
     *
     * @param dis the {@link DataInputStream} to read from.
     * @return the {@link BigInteger} read from the stream.
     * @throws IOException if an I/O error occurs, if the end of stream is reached prematurely,
     *                     or if the encoded length is negative (which might indicate data corruption
     *                     or an incompatible format).
     * @see #writeBigInteger(BigInteger, DataOutputStream)
     */
    public static BigInteger readBigInteger(DataInputStream dis) throws IOException {
        short length = dis.readShort();
        if (length < 0) {
            // A negative length is invalid for array allocation and indicates a format issue
            // or data corruption.
            throw new IOException(
                String.format("Invalid BigInteger length read from stream: %d. Length must be non-negative.",
                              length));
        }

        // Check against a reasonable maximum to prevent OutOfMemoryError with corrupted data
        // Short.MAX_VALUE is the theoretical max, but could still be very large.
        // For now, trust the length if it's valid short.
        if (length > Short.MAX_VALUE) { // Should not happen if length is short, but as sanity check
             throw new IOException(
                String.format("BigInteger length %d exceeds maximum of %d", length, Short.MAX_VALUE));
        }


        byte[] buf = new byte[length];
        dis.readFully(buf); // Throws EOFException if stream ends before all bytes are read.
        // Constructing with signum=1 ensures the BigInteger is positive,
        // matching the writeBigInteger constraint.
        return new BigInteger(1, buf);
    }

    /**
     * Converts a BigInteger to a hexadecimal string.
     * <p>
     * This method is provided as an alternative to BigInteger.toString(16) which had issues
     * with NPE on some JDK versions.
     * </p>
     *
     * @param bi the BigInteger to convert
     *
     * @return hexadecimal string representation
     */
    public static String biToHex(BigInteger bi) {
        return bytesToHex(bi.toByteArray());
    }
}
