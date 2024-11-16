package hyphanet.support;

import hyphanet.support.logger.Logger;
import hyphanet.support.logger.Logger.LogLevel;

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
                String.format("Total length: %d, offset: %d, requested length: %d", bs.length, off,
                              length));
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
     * Converts a hexadecimal string to a byte array with an offset.
     *
     * @param s   the hexadecimal string to convert
     * @param off the offset in the resulting byte array
     *
     * @return the byte array containing the converted values
     *
     * @throws NumberFormatException if the string contains invalid hex characters
     */
    public static byte[] hexToBytes(String s, int off) {
        byte[] bs = new byte[off + (1 + s.length()) / 2];
        hexToBytes(s, bs, off);
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
                "Output buffer too small for input (" + out.length + '<' + off + slen / 2 + ')');
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
     * Packs the bits in the BitSet into a byte array, considering the specified size starting from the
     * least significant bit (LSB).
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

        if (logDEBUG) {
            // "8 * bytesAlloc" would only allocate enough space for the number of bits
            // "2 * 8 * bytesAlloc" correctly allocates space for the string representation where each
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
            Logger.debug(HexUtil.class,
                         String.format("bytes: %d returned from bitsToBytes(%s,%d): %s for %s",
                                       bytesAlloc, ba, size, bytesToHex(b), sb));
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
     * Reads bits from a byte array into a BitSet.
     *
     * @param b       the byte array to read from
     * @param ba      the BitSet to write to
     * @param maxSize the maximum number of bits to read
     */
    public static void bytesToBits(byte[] b, BitSet ba, int maxSize) {
        if (logDEBUG) {
            Logger.debug(HexUtil.class, "bytesToBits(" + bytesToHex(b) + ",ba," + maxSize + ")");
        }

        int x = 0;
        for (byte bi : b) {
            for (int j = 0; j < 8; j++) {
                if (x > maxSize) {
                    break;
                }
                int mask = 1 << j;
                boolean value = (mask & bi) != 0;
                ba.set(x, value);
                x++;
            }
        }
    }

    /**
     * Converts a hexadecimal string to bits and stores them in a BitSet.
     *
     * @param s      hexadecimal string of the stored bits
     * @param ba     the BitSet to store the bits in
     * @param length the maximum number of bits to store
     */
    public static void hexToBits(String s, BitSet ba, int length) {
        byte[] b = hexToBytes(s);
        bytesToBits(b, ba, length);
    }

    /**
     * Writes a BigInteger to a DataOutputStream.
     *
     * @param integer the BigInteger to write
     * @param out     the stream to write to
     *
     * @throws IOException           if an I/O error occurs
     * @throws IllegalStateException if the BigInteger is negative or too long
     */
    public static void writeBigInteger(BigInteger integer, DataOutputStream out) throws IOException {
        if (integer.signum() == -1) {
            throw new IllegalStateException("Negative BigInteger not allowed");
        }

        byte[] buf = integer.toByteArray();
        if (buf.length > Short.MAX_VALUE) {
            throw new IllegalStateException("BigInteger too long: " + buf.length + " bytes");
        }

        out.writeShort((short) buf.length);
        out.write(buf);
    }

    /**
     * Reads a BigInteger from a DataInputStream.
     *
     * @param dis the stream to read from
     *
     * @return the read BigInteger
     *
     * @throws IOException if an I/O error occurs or the input is invalid
     */
    public static BigInteger readBigInteger(DataInputStream dis) throws IOException {
        short length = dis.readShort();
        if (length < 0) {
            throw new IOException("Invalid BigInteger length: " + length);
        }

        byte[] buf = new byte[length];
        dis.readFully(buf);
        return new BigInteger(1, buf);
    }

    /**
     * Converts a BigInteger to a hexadecimal string.
     * <p>
     * This method is provided as an alternative to BigInteger.toString(16) which had issues with NPE on
     * some JDK versions.
     * </p>
     *
     * @param bi the BigInteger to convert
     *
     * @return hexadecimal string representation
     */
    public static String biToHex(BigInteger bi) {
        return bytesToHex(bi.toByteArray());
    }

    final private static boolean logDEBUG = Logger.shouldLog(LogLevel.DEBUG, HexUtil.class);
}
