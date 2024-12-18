/*
 * Copyright (c) 1997, 1998 Systemics Ltd on behalf of
 * the Cryptix Development Team. All rights reserved.
 */
package hyphanet.crypt.blockcipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.util.Arrays;

//...........................................................................

/**
 * Rijndael --pronounced Reindaal-- is a variable block-size (128-, 192- and 256-bit), variable
 * key-size (128-, 192- and 256-bit) symmetric cipher.<p>
 * <p>
 * Rijndael was written by <a href="mailto:rijmen@esat.kuleuven.ac.be">Vincent Rijmen</a> and
 * <a href="mailto:Joan.Daemen@village.uunet.be">Joan Daemen</a>.<p>
 * <p>
 * Portions of this code are <b>Copyright</b> &copy; 1997, 1998
 * <a href="http://www.systemics.com/">Systemics Ltd</a> on behalf of the
 * <a href="http://www.systemics.com/docs/cryptix/">Cryptix Development Team</a>.
 * <br>All rights reserved.<p>
 *
 * @author Raif S. Naffah
 * @author Paulo S. L. M. Barreto
 * <p>
 * License is apparently available from http://www.cryptix.org/docs/license.html
 */
final class RijndaelAlgorithm // implicit no-argument constructor
{
    // Constants
    static final String ALGORITHM = "Rijndael";
    static final double VERSION = 0.1;
    static final String FULL_NAME = ALGORITHM + " ver. " + VERSION;
    private static final String NAME = "Rijndael_Algorithm";
    private static final boolean IN = true;
    private static final boolean OUT = false;
    private static final int BLOCK_SIZE = 16; // default block size in bytes

    // Lookup tables as immutable arrays
    private static final int[] alog = new int[256];
    private static final int[] log = new int[256];
    private static final byte[] S = new byte[256];
    private static final byte[] Si = new byte[256];
    private static final int[] T1 = new int[256];
    private static final int[] T2 = new int[256];
    private static final int[] T3 = new int[256];
    private static final int[] T4 = new int[256];
    private static final int[] T5 = new int[256];
    private static final int[] T6 = new int[256];
    private static final int[] T7 = new int[256];
    private static final int[] T8 = new int[256];
    private static final int[] U1 = new int[256];
    private static final int[] U2 = new int[256];
    private static final int[] U3 = new int[256];
    private static final int[] U4 = new int[256];
    private static final byte[] rcon = new byte[30];
    private static final int[][][] shifts =
        new int[][][]{{{0, 0}, {1, 3}, {2, 2}, {3, 1}}, {{0, 0}, {1, 5}, {2, 4}, {3, 3}},
            {{0, 0}, {1, 7}, {3, 5}, {4, 4}}};
    private static final char[] HEX_DIGITS =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final Logger logger = LoggerFactory.getLogger(RijndaelAlgorithm.class);

    static {
        long time = System.currentTimeMillis();

        logger.trace("Algorithm Name: " + FULL_NAME);
        logger.trace("Electronic Codebook (ECB) Mode");
        int root = 0x11B;
        int i;
        int j;

        //
        // produce log and alog tables, needed for multiplying in the
        // field GF(2^m) (generator = 3)
        //
        generateLogAndAlogTables(root);
        generateSBoxes();

        //
        // T-boxes
        //
        byte[][] g = new byte[][]{{2, 1, 1, 3}, {3, 2, 1, 1}, {1, 3, 2, 1}, {1, 1, 3, 2}};
        byte[][] iG = generateInvertedGMatrix(g);
        generateTBoxes(g, iG);

        //
        // round constants
        //
        rcon[0] = 1;
        int r = 1;
        for (int t = 1; t < 30; t++) {
            r = mul(2, r);
            rcon[t] = (byte) r;
        }

        time = System.currentTimeMillis() - time;

        if (logger.isDebugEnabled()) {
            logger.debug("==========");
            logger.debug("Static Data");
            logger.debug("S[]:");
            for (i = 0; i < 16; i++) {
                for (j = 0; j < 16; j++) {
                    logger.debug("0x{}, ", byteToString(S[i * 16 + j]));
                }
            }
            logger.debug("Si[]:");
            for (i = 0; i < 16; i++) {
                for (j = 0; j < 16; j++) {
                    logger.debug("0x{}, ", byteToString(Si[i * 16 + j]));
                }
            }

            logger.debug("iG[]:");
            for (i = 0; i < 4; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", byteToString(iG[i][j]));
                }
            }

            logger.debug("T1[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T1[i * 4 + j]));
                }
            }
            logger.debug("T2[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T2[i * 4 + j]));
                }
            }
            logger.debug("T3[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T3[i * 4 + j]));
                }
            }
            logger.debug("T4[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T4[i * 4 + j]));
                }
            }
            logger.debug("T5[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T5[i * 4 + j]));
                }
            }
            logger.debug("T6[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T6[i * 4 + j]));
                }
            }
            logger.debug("T7[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T7[i * 4 + j]));
                }
            }
            logger.debug("T8[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(T8[i * 4 + j]));
                }
            }

            logger.debug("U1[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(U1[i * 4 + j]));
                }
            }
            logger.debug("U2[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(U2[i * 4 + j]));
                }
            }
            logger.debug("U3[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(U3[i * 4 + j]));
                }
            }
            logger.debug("U4[]:");
            for (i = 0; i < 64; i++) {
                for (j = 0; j < 4; j++) {
                    logger.debug("0x{}, ", intToString(U4[i * 4 + j]));
                }
            }

            logger.debug("rcon[]:");
            for (i = 0; i < 5; i++) {
                for (j = 0; j < 6; j++) {
                    logger.debug("0x{}, ", byteToString(rcon[i * 6 + j]));
                }
            }

            logger.debug("Total initialization time: {} ms.", time);
        }
    }

    public static void main(String[] args) {
        self_test(16);
        self_test(24);
        self_test(32);
    }


    //	Static code - to intialise S-boxes and T-boxes
    //	...........................................................................

    /**
     * Expand a user-supplied key material into a session key.
     *
     * @param k         The 128/192/256-bit user-key to use.
     * @param blockSize The block size in bytes of this Rijndael.
     *
     * @throws InvalidKeyException If the key is invalid.
     */
    //TODO: This method doesn't really need synchronization. The only reason
    //I can see for it to be synchronized is that it will consume 100% CPU (due to
    //heavy calculations) when called. Probably should be unsynchronized if we
    //want better support for dual+ CPU machines. /Iakin 2003-10-12
    //Concur:  the class has no fields which are not final, and does
    //not reference fields of any other classes.  Control over how
    //many simultaneous makeKey invocations should be allowed is
    //a problem the callers should resolve among themselves.
    //It is a fact that allowing no more than one makeKey on any given
    //CPU will result in fewer cache misses.  -- ejhuff 2003-10-12
    static synchronized Object makeKey(byte[] k, int blockSize) throws InvalidKeyException {
        if (logger.isTraceEnabled()) {
            trace(IN, "makeKey(" + Arrays.toString(k) + ", " + blockSize + ')');
        }
        if (k == null) {
            throw new InvalidKeyException("Empty key");
        }
        if (!((k.length == 16) || (k.length == 24) || (k.length == 32))) {
            throw new InvalidKeyException("Incorrect key length");
        }
        int rounds = getRounds(k.length, blockSize);
        int bc = blockSize / 4;
        final int bcShift;
        switch (bc) {
            case 4 -> bcShift = 2;
            case 8 -> bcShift = 3;
            default -> throw new InvalidKeyException("Unsupported block size: " + blockSize);
        }
        int[][] ke = new int[rounds + 1][bc]; // encryption round keys
        int[][] kd = new int[rounds + 1][bc]; // decryption round keys
        int roundKeyCount = (rounds + 1) << bcShift;
        int kc = k.length / 4;
        int[] tk = new int[kc];
        int i;
        int j;

        // copy user material bytes into temporary ints
        for (i = 0, j = 0; i < kc; ) {
            tk[i++] = (k[j++] & 0xFF) << 24 | (k[j++] & 0xFF) << 16 | (k[j++] & 0xFF) << 8 |
                      (k[j++] & 0xFF);
        }
        // copy values into round key arrays
        int t = 0;
        for (j = 0; (j < kc) && (t < roundKeyCount); j++, t++) {
            ke[t >>> bcShift][t & (bc - 1)] = tk[j];
            kd[rounds - (t >>> bcShift)][t & (bc - 1)] = tk[j];
        }
        int tt;
        int rconpointer = 0;
        while (t < roundKeyCount) {
            // extrapolate using phi (the round key evolution function)
            tt = tk[kc - 1];
            tk[0] ^=
                (S[(tt >>> 16) & 0xFF] & 0xFF) << 24 ^ (S[(tt >>> 8) & 0xFF] & 0xFF) << 16 ^
                (S[tt & 0xFF] & 0xFF) << 8 ^ (S[(tt >>> 24) & 0xFF] & 0xFF) ^
                (rcon[rconpointer++] & 0xFF) << 24;
            if (kc != 8) {
                for (i = 1, j = 0; i < kc; ) {
                    //tk[i++] ^= tk[j++];
                    // The above line replaced with the code below in order to work around
                    // a bug in the kjc-1.4F java compiler (which has been reported).
                    tk[i] ^= tk[j++];
                    i++;
                }
            } else {
                for (i = 1, j = 0; i < kc / 2; ) {
                    //tk[i++] ^= tk[j++];
                    // The above line replaced with the code below in order to work around
                    // a bug in the kjc-1.4F java compiler (which has been reported).
                    tk[i] ^= tk[j++];
                    i++;
                }
                tt = tk[kc / 2 - 1];
                tk[kc / 2] ^= (S[tt & 0xFF] & 0xFF) ^ (S[(tt >>> 8) & 0xFF] & 0xFF) << 8 ^
                              (S[(tt >>> 16) & 0xFF] & 0xFF) << 16 ^
                              (S[(tt >>> 24) & 0xFF] & 0xFF) << 24;
                for (j = kc / 2, i = j + 1; i < kc; ) {
                    //tk[i++] ^= tk[j++];
                    // The above line replaced with the code below in order to work around
                    // a bug in the kjc-1.4F java compiler (which has been reported).
                    tk[i] ^= tk[j++];
                    i++;
                }
            }
            // copy values into round key arrays
            for (j = 0; (j < kc) && (t < roundKeyCount); j++, t++) {
                ke[t >>> bcShift][t & (bc - 1)] = tk[j];
                kd[rounds - (t >>> bcShift)][t & (bc - 1)] = tk[j];
            }
        }
        for (int r = 1; r < rounds; r++)    // inverse MixColumn where needed
        {
            for (j = 0; j < bc; j++) {
                tt = kd[r][j];
                kd[r][j] =
                    U1[(tt >>> 24) & 0xFF] ^ U2[(tt >>> 16) & 0xFF] ^ U3[(tt >>> 8) & 0xFF] ^
                    U4[tt & 0xFF];
            }
        }
        // assemble the encryption (Ke) and decryption (Kd) round keys into
        // one sessionKey object
        Object[] sessionKey = new Object[]{ke, kd};
        trace(OUT, "makeKey()");
        return sessionKey;
    }

    /**
     * Encrypt exactly one block of plaintext.
     *
     * @param in         The plaintext.
     * @param result     The buffer into which to write the resulting ciphertext.
     * @param inOffset   Index of in from which to start considering data.
     * @param sessionKey The session key to use for encryption.
     * @param blockSize  The block size in bytes of this Rijndael.
     */
    static void blockEncrypt(
        byte[] in, byte[] result, int inOffset, Object sessionKey, int blockSize) {
        if (blockSize == BLOCK_SIZE) {
            blockEncrypt(in, result, inOffset, sessionKey);
            return;
        }
        if (blockSize == 256 / 8) {
            blockEncrypt256(in, result, inOffset, sessionKey);
            return;
        }
        if (logger.isTraceEnabled()) {
            trace(IN,
                  "blockEncrypt(" + Arrays.toString(in) + ", " + inOffset + ", " + sessionKey +
                  ", " + blockSize + ')');
        }
        Object[] sKey = (Object[]) sessionKey; // extract encryption round keys
        int[][] ke = (int[][]) sKey[0];

        int bc = blockSize / 4;
        int rounds = ke.length - 1;
        int sc = switch (bc) {
            case 4 -> 0;
            case 6 -> 1;
            default -> 2;
        };
        int s1 = shifts[sc][1][0];
        int s2 = shifts[sc][2][0];
        int s3 = shifts[sc][3][0];
        int[] a = new int[bc];
        int[] t = new int[bc]; // temporary work array
        int i;
        int j = 0;
        int tt;

        for (i = 0; i < bc; i++)                   // plaintext to ints + key
        {
            t[i] = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                    (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ke[0][i];
        }
        for (int r = 1; r < rounds; r++) {          // apply round transforms
            for (i = 0; i < bc; i++) {
                a[i] = (T1[(t[i] >>> 24) & 0xFF] ^ T2[(t[(i + s1) % bc] >>> 16) & 0xFF] ^
                        T3[(t[(i + s2) % bc] >>> 8) & 0xFF] ^ T4[t[(i + s3) % bc] & 0xFF]) ^
                       ke[r][i];
            }
            System.arraycopy(a, 0, t, 0, bc);
            if (logger.isTraceEnabled()) {
                logger.trace("CT{}={}", r, toString(t));
            }
        }
        for (i = 0; i < bc; i++) {                   // last round is special
            tt = ke[rounds][i];
            result[j++] = (byte) ((S[(t[i] >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
            result[j++] = (byte) ((S[(t[(i + s1) % bc] >>> 16) & 0xFF] & 0XFF) ^ (tt >>> 16));
            result[j++] = (byte) ((S[(t[(i + s2) % bc] >>> 8) & 0xFF] & 0XFF) ^ (tt >>> 8));
            result[j++] = (byte) (S[t[(i + s3) % bc] & 0xFF] ^ tt);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("CT={}", toString(result));
        }
        trace(OUT, "blockEncrypt()");
    }

    /**
     * Decrypt exactly one block of ciphertext.
     *
     * @param in         The ciphertext.
     * @param result     The resulting ciphertext.
     * @param inOffset   Index of in from which to start considering data.
     * @param sessionKey The session key to use for decryption.
     * @param blockSize  The block size in bytes of this Rijndael.
     */
    static void blockDecrypt(
        byte[] in, byte[] result, int inOffset, Object sessionKey, int blockSize) {
        if (blockSize == BLOCK_SIZE) {
            blockDecrypt(in, result, inOffset, sessionKey);
            return;
        }
        if (blockSize == 256 / 8) {
            blockDecrypt256(in, result, inOffset, sessionKey);
            return;
        }

        if (logger.isTraceEnabled()) {
            trace(IN,
                  "blockDecrypt(" + Arrays.toString(in) + ", " + inOffset + ", " + sessionKey +
                  ", " + blockSize + ')');
        }
        Object[] sKey = (Object[]) sessionKey; // extract decryption round keys
        int[][] kd = (int[][]) sKey[1];

        int bc = blockSize / 4;
        int rounds = kd.length - 1;
        int sc = switch (bc) {
            case 4 -> 0;
            case 6 -> 1;
            default -> 2;
        };
        int s1 = shifts[sc][1][1];
        int s2 = shifts[sc][2][1];
        int s3 = shifts[sc][3][1];
        int[] a = new int[bc];
        int[] t = new int[bc]; // temporary work array
        int i;
        int j = 0;
        int tt;

        for (i = 0; i < bc; i++)                   // ciphertext to ints + key
        {
            t[i] = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                    (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kd[0][i];
        }
        for (int r = 1; r < rounds; r++) {          // apply round transforms
            for (i = 0; i < bc; i++) {
                a[i] = (T5[(t[i] >>> 24) & 0xFF] ^ T6[(t[(i + s1) % bc] >>> 16) & 0xFF] ^
                        T7[(t[(i + s2) % bc] >>> 8) & 0xFF] ^ T8[t[(i + s3) % bc] & 0xFF]) ^
                       kd[r][i];
            }
            System.arraycopy(a, 0, t, 0, bc);
            if (logger.isTraceEnabled()) {
                logger.trace("PT{}={}", r, toString(t));
            }
        }
        for (i = 0; i < bc; i++) {                   // last round is special
            tt = kd[rounds][i];
            result[j++] = (byte) ((Si[(t[i] >>> 24) & 0xFF] & 0XFF) ^ (tt >>> 24));
            result[j++] = (byte) ((Si[(t[(i + s1) % bc] >>> 16) & 0xFF] & 0XFF) ^ (tt >>> 16));
            result[j++] = (byte) ((Si[(t[(i + s2) % bc] >>> 8) & 0xFF] & 0XFF) ^ (tt >>> 8));
            result[j++] = (byte) (Si[t[(i + s3) % bc] & 0xFF] ^ tt);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("PT={}", toString(result));
        }
        trace(OUT, "blockDecrypt()");
    }

    private static void debug(String s) {
        logger.debug(">>> " + NAME + ": {}", s);
    }

    private static void trace(boolean in, String s) {
        logger.trace("{}{}.{}", in ? "==> " : "<== ", NAME, s);
    }


    //	Basic API methods
    //	...........................................................................

    private static void generateLogAndAlogTables(int root) {
        alog[0] = 1;
        for (int i = 1; i < 256; i++) {
            int j = (alog[i - 1] << 1) ^ alog[i - 1];
            if ((j & 0x100) != 0) {
                j ^= root;
            }
            alog[i] = j;
        }
        for (int i = 1; i < 255; i++) {
            log[alog[i]] = i;
        }
    }

    private static void generateSBoxes() {
        byte[][] a = new byte[][]{{1, 1, 1, 1, 1, 0, 0, 0}, {0, 1, 1, 1, 1, 1, 0, 0},
            {0, 0, 1, 1, 1, 1, 1, 0}, {0, 0, 0, 1, 1, 1, 1, 1}, {1, 0, 0, 0, 1, 1, 1, 1},
            {1, 1, 0, 0, 0, 1, 1, 1}, {1, 1, 1, 0, 0, 0, 1, 1}, {1, 1, 1, 1, 0, 0, 0, 1}};
        byte[] b = new byte[]{0, 1, 1, 0, 0, 0, 1, 1};

        //
        // substitution box based on F^{-1}(x)
        //
        byte[][] box = new byte[256][8];
        box[1][7] = 1;
        for (int i = 2; i < 256; i++) {
            int j = alog[255 - log[i]];
            for (int t = 0; t < 8; t++) {
                box[i][t] = (byte) ((j >>> (7 - t)) & 0x01);
            }
        }
        //
        // affine transform:  box[i] <- B + A*box[i]
        //
        byte[][] cox = new byte[256][8];
        for (int i = 0; i < 256; i++) {
            for (int t = 0; t < 8; t++) {
                cox[i][t] = b[t];
                for (int j = 0; j < 8; j++) {
                    cox[i][t] ^= (byte) (a[t][j] * box[i][j]);
                }
            }
        }
        //
        // S-boxes and inverse S-boxes
        //
        for (int i = 0; i < 256; i++) {
            S[i] = (byte) (cox[i][0] << 7);
            for (int t = 1; t < 8; t++) {
                S[i] ^= (byte) (cox[i][t] << (7 - t));
            }
            Si[S[i] & 0xFF] = (byte) i;
        }
    }

    private static byte[][] generateInvertedGMatrix(byte[][] gMatrix) {
        byte[][] aa = new byte[4][8];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(gMatrix[i], 0, aa[i], 0, 4);
            aa[i][i + 4] = 1;
        }
        byte pivot;
        byte tmp;
        byte[][] iG = new byte[4][4];
        for (int i = 0; i < 4; i++) {
            pivot = aa[i][i];
            if (pivot == 0) {
                int t = i + 1;
                while (aa[t][i] == 0) {
                    t++;
                }
                for (int j = 0; j < 8; j++) {
                    tmp = aa[i][j];
                    aa[i][j] = aa[t][j];
                    aa[t][j] = tmp;
                }
                pivot = aa[i][i];
            }
            for (int j = 0; j < 8; j++) {
                if (aa[i][j] != 0) {
                    aa[i][j] =
                        (byte) alog[(255 + log[aa[i][j] & 0xFF] - log[pivot & 0xFF]) % 255];
                }
            }
            for (int t = 0; t < 4; t++) {
                if (i != t) {
                    for (int j = i + 1; j < 8; j++) {
                        aa[t][j] ^= (byte) mul(aa[i][j], aa[t][i]);
                    }
                    aa[t][i] = 0;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            System.arraycopy(aa[i], 4, iG[i], 0, 4);
        }

        return iG;
    }

    private static void generateTBoxes(byte[][] g, byte[][] iG) {
        for (int t = 0; t < 256; t++) {
            int s = S[t];
            T1[t] = mul4(s, g[0]);
            T2[t] = mul4(s, g[1]);
            T3[t] = mul4(s, g[2]);
            T4[t] = mul4(s, g[3]);

            s = Si[t];
            T5[t] = mul4(s, iG[0]);
            T6[t] = mul4(s, iG[1]);
            T7[t] = mul4(s, iG[2]);
            T8[t] = mul4(s, iG[3]);

            U1[t] = mul4(t, iG[0]);
            U2[t] = mul4(t, iG[1]);
            U3[t] = mul4(t, iG[2]);
            U4[t] = mul4(t, iG[3]);
        }
    }

    // multiply two elements of GF(2^m)
    private static int mul(int a, int b) {
        return ((a != 0) && (b != 0)) ? alog[(log[a & 0xFF] + log[b & 0xFF]) % 255] : 0;
    }


    //	Rijndael own methods
    //	...........................................................................

    // convenience method used in generating Transposition boxes
    private static int mul4(int a, byte[] b) {
        if (a == 0) {
            return 0;
        }
        a = log[a & 0xFF];
        int a0 = (b[0] != 0) ? alog[(a + log[b[0] & 0xFF]) % 255] & 0xFF : 0;
        int a1 = (b[1] != 0) ? alog[(a + log[b[1] & 0xFF]) % 255] & 0xFF : 0;
        int a2 = (b[2] != 0) ? alog[(a + log[b[2] & 0xFF]) % 255] & 0xFF : 0;
        int a3 = (b[3] != 0) ? alog[(a + log[b[3] & 0xFF]) % 255] & 0xFF : 0;
        return a0 << 24 | a1 << 16 | a2 << 8 | a3;
    }

    /**
     * Convenience method to encrypt exactly one block of plaintext, assuming Rijndael's
     * default block size (128-bit).
     *
     * @param in         The plaintext.
     * @param result     The buffer into which to write the resulting ciphertext.
     * @param inOffset   Index of in from which to start considering data.
     * @param sessionKey The session key to use for encryption.
     */
    private static void blockEncrypt(
        byte[] in, byte[] result, int inOffset, Object sessionKey) {
        if (logger.isTraceEnabled()) {
            trace(IN,
                  "blockEncrypt(" + Arrays.toString(in) + ", " + inOffset + ", " + sessionKey +
                  ')');
        }
        int[][] ke = (int[][]) ((Object[]) sessionKey)[0]; // extract encryption round keys
        int rounds = ke.length - 1;
        int[] ker = ke[0];

        // plaintext to ints + key
        int t0 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[0];
        int t1 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[1];
        int t2 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[2];
        int t3 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset] & 0xFF)) ^ ker[3];

        int a0;
        int a1;
        int a2;
        int a3;
        for (int r = 1; r < rounds; r++) {          // apply round transforms
            ker = ke[r];
            a0 = (T1[(t0 >>> 24) & 0xFF] ^ T2[(t1 >>> 16) & 0xFF] ^ T3[(t2 >>> 8) & 0xFF] ^
                  T4[t3 & 0xFF]) ^ ker[0];
            a1 = (T1[(t1 >>> 24) & 0xFF] ^ T2[(t2 >>> 16) & 0xFF] ^ T3[(t3 >>> 8) & 0xFF] ^
                  T4[t0 & 0xFF]) ^ ker[1];
            a2 = (T1[(t2 >>> 24) & 0xFF] ^ T2[(t3 >>> 16) & 0xFF] ^ T3[(t0 >>> 8) & 0xFF] ^
                  T4[t1 & 0xFF]) ^ ker[2];
            a3 = (T1[(t3 >>> 24) & 0xFF] ^ T2[(t0 >>> 16) & 0xFF] ^ T3[(t1 >>> 8) & 0xFF] ^
                  T4[t2 & 0xFF]) ^ ker[3];
            t0 = a0;
            t1 = a1;
            t2 = a2;
            t3 = a3;
            if (logger.isTraceEnabled()) {
                logger.trace("CT{}={}{}{}{}", r, intToString(t0), intToString(t1),
                             intToString(t2), intToString(t3));
            }
        }

        // last round is special
        ker = ke[rounds];
        int tt = ker[0];
        result[0] = (byte) ((S[(t0 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[1] = (byte) ((S[(t1 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[2] = (byte) ((S[(t2 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[3] = (byte) (S[t3 & 0xFF] ^ tt);
        tt = ker[1];
        result[4] = (byte) ((S[(t1 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[5] = (byte) ((S[(t2 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[6] = (byte) ((S[(t3 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[7] = (byte) (S[t0 & 0xFF] ^ tt);
        tt = ker[2];
        result[8] = (byte) ((S[(t2 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[9] = (byte) ((S[(t3 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[10] = (byte) ((S[(t0 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[11] = (byte) (S[t1 & 0xFF] ^ tt);
        tt = ker[3];
        result[12] = (byte) ((S[(t3 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[13] = (byte) ((S[(t0 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[14] = (byte) ((S[(t1 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[15] = (byte) (S[t2 & 0xFF] ^ tt);
        if (logger.isTraceEnabled()) {
            logger.trace("CT={}", toString(result));
        }
        trace(OUT, "blockEncrypt()");
    }

    /**
     * Convenience method to encrypt exactly one block of plaintext, assuming Rijndael's
     * non-standard block size 256 bit).
     *
     * @param in         The plaintext.
     * @param result     The buffer into which to write the resulting ciphertext.
     * @param inOffset   Index of in from which to start considering data.
     * @param sessionKey The session key to use for encryption.
     */
    private static void blockEncrypt256(
        byte[] in, byte[] result, int inOffset, Object sessionKey) {
        if (logger.isTraceEnabled()) {
            trace(IN, "blockEncrypt256(" + Arrays.toString(in) + ", " + inOffset + ", " +
                      sessionKey + ')');
        }
        int[][] ke = (int[][]) ((Object[]) sessionKey)[0]; // extract encryption round keys
        int rounds = ke.length - 1;
        int[] ker = ke[0];

        // plaintext to ints + key
        int t0 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[0];
        int t1 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[1];
        int t2 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[2];
        int t3 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[3];
        int t4 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[4];
        int t5 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[5];
        int t6 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ ker[6];
        int t7 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset] & 0xFF)) ^ ker[7];

        int a0;
        int a1;
        int a2;
        int a3;
        int a4;
        int a5;
        int a6;
        int a7;
        for (int r = 1; r < rounds; r++) {          // apply round transforms
            ker = ke[r];
            a0 = (T1[(t0 >>> 24) & 0xFF] ^ T2[(t1 >>> 16) & 0xFF] ^ T3[(t3 >>> 8) & 0xFF] ^
                  T4[t4 & 0xFF]) ^ ker[0];

            a1 = (T1[(t1 >>> 24) & 0xFF] ^ T2[(t2 >>> 16) & 0xFF] ^ T3[(t4 >>> 8) & 0xFF] ^
                  T4[t5 & 0xFF]) ^ ker[1];

            a2 = (T1[(t2 >>> 24) & 0xFF] ^ T2[(t3 >>> 16) & 0xFF] ^ T3[(t5 >>> 8) & 0xFF] ^
                  T4[t6 & 0xFF]) ^ ker[2];

            a3 = (T1[(t3 >>> 24) & 0xFF] ^ T2[(t4 >>> 16) & 0xFF] ^ T3[(t6 >>> 8) & 0xFF] ^
                  T4[t7 & 0xFF]) ^ ker[3];

            a4 = (T1[(t4 >>> 24) & 0xFF] ^ T2[(t5 >>> 16) & 0xFF] ^ T3[(t7 >>> 8) & 0xFF] ^
                  T4[t0 & 0xFF]) ^ ker[4];

            a5 = (T1[(t5 >>> 24) & 0xFF] ^ T2[(t6 >>> 16) & 0xFF] ^ T3[(t0 >>> 8) & 0xFF] ^
                  T4[t1 & 0xFF]) ^ ker[5];

            a6 = (T1[(t6 >>> 24) & 0xFF] ^ T2[(t7 >>> 16) & 0xFF] ^ T3[(t1 >>> 8) & 0xFF] ^
                  T4[t2 & 0xFF]) ^ ker[6];

            a7 = (T1[(t7 >>> 24) & 0xFF] ^ T2[(t0 >>> 16) & 0xFF] ^ T3[(t2 >>> 8) & 0xFF] ^
                  T4[t3 & 0xFF]) ^ ker[7];
            t0 = a0;
            t1 = a1;
            t2 = a2;
            t3 = a3;
            t4 = a4;
            t5 = a5;
            t6 = a6;
            t7 = a7;
            if (logger.isTraceEnabled()) {
                logger.trace("CT{}={}{}{}{}", r, intToString(t0), intToString(t1),
                             intToString(t2), intToString(t3));
            }
        }

        // last round is special
        ker = ke[rounds];
        int tt = ker[0];
        result[0] = (byte) ((S[(t0 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[1] = (byte) ((S[(t1 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[2] = (byte) ((S[(t3 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[3] = (byte) (S[t4 & 0xFF] ^ tt);
        tt = ker[1];
        result[4] = (byte) ((S[(t1 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[5] = (byte) ((S[(t2 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[6] = (byte) ((S[(t4 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[7] = (byte) (S[t5 & 0xFF] ^ tt);
        tt = ker[2];
        result[8] = (byte) ((S[(t2 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[9] = (byte) ((S[(t3 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[10] = (byte) ((S[(t5 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[11] = (byte) (S[t6 & 0xFF] ^ tt);
        tt = ker[3];
        result[12] = (byte) ((S[(t3 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[13] = (byte) ((S[(t4 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[14] = (byte) ((S[(t6 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[15] = (byte) (S[t7 & 0xFF] ^ tt);
        tt = ker[4];
        result[16] = (byte) ((S[(t4 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[17] = (byte) ((S[(t5 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[18] = (byte) ((S[(t7 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[19] = (byte) (S[t0 & 0xFF] ^ tt);
        tt = ker[5];
        result[20] = (byte) ((S[(t5 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[21] = (byte) ((S[(t6 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[22] = (byte) ((S[(t0 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[23] = (byte) (S[t1 & 0xFF] ^ tt);
        tt = ker[6];
        result[24] = (byte) ((S[(t6 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[25] = (byte) ((S[(t7 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[26] = (byte) ((S[(t1 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[27] = (byte) (S[t2 & 0xFF] ^ tt);
        tt = ker[7];
        result[28] = (byte) ((S[(t7 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[29] = (byte) ((S[(t0 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[30] = (byte) ((S[(t2 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[31] = (byte) (S[t3 & 0xFF] ^ tt);
        if (logger.isTraceEnabled()) {
            logger.trace("CT={}", toString(result));
        }
        trace(OUT, "blockEncrypt()");
    }

    /**
     * Convenience method to decrypt exactly one block of plaintext, assuming Rijndael's
     * default block size (128-bit).
     *
     * @param in         The ciphertext.
     * @param result     the resulting ciphertext
     * @param inOffset   Index of in from which to start considering data.
     * @param sessionKey The session key to use for decryption.
     */
    private static void blockDecrypt(
        byte[] in, byte[] result, int inOffset, Object sessionKey) {
        if (logger.isTraceEnabled()) {
            trace(IN,
                  "blockDecrypt(" + Arrays.toString(in) + ", " + inOffset + ", " + sessionKey +
                  ')');
        }
        int[][] kd = (int[][]) ((Object[]) sessionKey)[1]; // extract decryption round keys
        int rounds = kd.length - 1;
        int[] kdr = kd[0];

        // ciphertext to ints + key
        int t0 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[0];
        int t1 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[1];
        int t2 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[2];
        int t3 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset] & 0xFF)) ^ kdr[3];

        int a0;
        int a1;
        int a2;
        int a3;
        for (int r = 1; r < rounds; r++) {          // apply round transforms
            kdr = kd[r];
            a0 = (T5[(t0 >>> 24) & 0xFF] ^ T6[(t3 >>> 16) & 0xFF] ^ T7[(t2 >>> 8) & 0xFF] ^
                  T8[t1 & 0xFF]) ^ kdr[0];
            a1 = (T5[(t1 >>> 24) & 0xFF] ^ T6[(t0 >>> 16) & 0xFF] ^ T7[(t3 >>> 8) & 0xFF] ^
                  T8[t2 & 0xFF]) ^ kdr[1];
            a2 = (T5[(t2 >>> 24) & 0xFF] ^ T6[(t1 >>> 16) & 0xFF] ^ T7[(t0 >>> 8) & 0xFF] ^
                  T8[t3 & 0xFF]) ^ kdr[2];
            a3 = (T5[(t3 >>> 24) & 0xFF] ^ T6[(t2 >>> 16) & 0xFF] ^ T7[(t1 >>> 8) & 0xFF] ^
                  T8[t0 & 0xFF]) ^ kdr[3];
            t0 = a0;
            t1 = a1;
            t2 = a2;
            t3 = a3;
            if (logger.isTraceEnabled()) {
                logger.trace("PT{}={}{}{}{}", r, intToString(t0), intToString(t1),
                             intToString(t2), intToString(t3));
            }
        }

        // last round is special
        kdr = kd[rounds];
        int tt = kdr[0];
        result[0] = (byte) ((Si[(t0 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[1] = (byte) ((Si[(t3 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[2] = (byte) ((Si[(t2 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[3] = (byte) (Si[t1 & 0xFF] ^ tt);
        tt = kdr[1];
        result[4] = (byte) ((Si[(t1 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[5] = (byte) ((Si[(t0 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[6] = (byte) ((Si[(t3 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[7] = (byte) (Si[t2 & 0xFF] ^ tt);
        tt = kdr[2];
        result[8] = (byte) ((Si[(t2 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[9] = (byte) ((Si[(t1 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[10] = (byte) ((Si[(t0 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[11] = (byte) (Si[t3 & 0xFF] ^ tt);
        tt = kdr[3];
        result[12] = (byte) ((Si[(t3 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[13] = (byte) ((Si[(t2 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[14] = (byte) ((Si[(t1 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[15] = (byte) (Si[t0 & 0xFF] ^ tt);
        if (logger.isTraceEnabled()) {
            logger.trace("PT={}", toString(result));
        }
        trace(OUT, "blockDecrypt()");
    }

    /**
     * Convenience method to decrypt exactly one block of plaintext, assuming Rijndael's
     * non-standard block size 256 bit.
     *
     * @param in         The ciphertext.
     * @param result     the resulting ciphertext
     * @param inOffset   Index of in from which to start considering data.
     * @param sessionKey The session key to use for decryption.
     */
    private static void blockDecrypt256(
        byte[] in, byte[] result, int inOffset, Object sessionKey) {
        if (logger.isTraceEnabled()) {
            trace(IN,
                  "blockDecrypt(" + Arrays.toString(in) + ", " + inOffset + ", " + sessionKey +
                  ')');
        }
        int[][] kd = (int[][]) ((Object[]) sessionKey)[1]; // extract decryption round keys
        int rounds = kd.length - 1;
        int[] kdr = kd[0];

        // ciphertext to ints + key
        int t0 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[0];
        int t1 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[1];
        int t2 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[2];
        int t3 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[3];
        int t4 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[4];
        int t5 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[5];
        int t6 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset++] & 0xFF)) ^ kdr[6];
        int t7 = ((in[inOffset++] & 0xFF) << 24 | (in[inOffset++] & 0xFF) << 16 |
                  (in[inOffset++] & 0xFF) << 8 | (in[inOffset] & 0xFF)) ^ kdr[7];

        int a0;
        int a1;
        int a2;
        int a3;
        int a4;
        int a5;
        int a6;
        int a7;
        for (int r = 1; r < rounds; r++) {          // apply round transforms
            kdr = kd[r];
            a0 = (T5[(t0 >>> 24) & 0xFF] ^ T6[(t7 >>> 16) & 0xFF] ^ T7[(t5 >>> 8) & 0xFF] ^
                  T8[t4 & 0xFF]) ^ kdr[0];

            a1 = (T5[(t1 >>> 24) & 0xFF] ^ T6[(t0 >>> 16) & 0xFF] ^ T7[(t6 >>> 8) & 0xFF] ^
                  T8[t5 & 0xFF]) ^ kdr[1];

            a2 = (T5[(t2 >>> 24) & 0xFF] ^ T6[(t1 >>> 16) & 0xFF] ^ T7[(t7 >>> 8) & 0xFF] ^
                  T8[t6 & 0xFF]) ^ kdr[2];

            a3 = (T5[(t3 >>> 24) & 0xFF] ^ T6[(t2 >>> 16) & 0xFF] ^ T7[(t0 >>> 8) & 0xFF] ^
                  T8[t7 & 0xFF]) ^ kdr[3];

            a4 = (T5[(t4 >>> 24) & 0xFF] ^ T6[(t3 >>> 16) & 0xFF] ^ T7[(t1 >>> 8) & 0xFF] ^
                  T8[t0 & 0xFF]) ^ kdr[4];

            a5 = (T5[(t5 >>> 24) & 0xFF] ^ T6[(t4 >>> 16) & 0xFF] ^ T7[(t2 >>> 8) & 0xFF] ^
                  T8[t1 & 0xFF]) ^ kdr[5];

            a6 = (T5[(t6 >>> 24) & 0xFF] ^ T6[(t5 >>> 16) & 0xFF] ^ T7[(t3 >>> 8) & 0xFF] ^
                  T8[t2 & 0xFF]) ^ kdr[6];

            a7 = (T5[(t7 >>> 24) & 0xFF] ^ T6[(t6 >>> 16) & 0xFF] ^ T7[(t4 >>> 8) & 0xFF] ^
                  T8[t3 & 0xFF]) ^ kdr[7];
            t0 = a0;
            t1 = a1;
            t2 = a2;
            t3 = a3;
            t4 = a4;
            t5 = a5;
            t6 = a6;
            t7 = a7;
            if (logger.isTraceEnabled()) {
                logger.trace("PT{}={}{}{}{}", r, intToString(t0), intToString(t1),
                             intToString(t2), intToString(t3));
            }
        }

        // last round is special
        kdr = kd[rounds];
        int tt = kdr[0];
        result[0] = (byte) ((Si[(t0 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[1] = (byte) ((Si[(t7 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[2] = (byte) ((Si[(t5 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[3] = (byte) (Si[t4 & 0xFF] ^ tt);
        tt = kdr[1];
        result[4] = (byte) ((Si[(t1 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[5] = (byte) ((Si[(t0 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[6] = (byte) ((Si[(t6 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[7] = (byte) (Si[t5 & 0xFF] ^ tt);
        tt = kdr[2];
        result[8] = (byte) ((Si[(t2 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[9] = (byte) ((Si[(t1 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[10] = (byte) ((Si[(t7 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[11] = (byte) (Si[t6 & 0xFF] ^ tt);
        tt = kdr[3];
        result[12] = (byte) ((Si[(t3 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[13] = (byte) ((Si[(t2 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[14] = (byte) ((Si[(t0 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[15] = (byte) (Si[t7 & 0xFF] ^ tt);
        tt = kdr[4];
        result[16] = (byte) ((Si[(t4 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[17] = (byte) ((Si[(t3 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[18] = (byte) ((Si[(t1 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[19] = (byte) (Si[t0 & 0xFF] ^ tt);
        tt = kdr[5];
        result[20] = (byte) ((Si[(t5 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[21] = (byte) ((Si[(t4 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[22] = (byte) ((Si[(t2 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[23] = (byte) (Si[t1 & 0xFF] ^ tt);
        tt = kdr[6];
        result[24] = (byte) ((Si[(t6 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[25] = (byte) ((Si[(t5 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[26] = (byte) ((Si[(t3 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[27] = (byte) (Si[t2 & 0xFF] ^ tt);
        tt = kdr[7];
        result[28] = (byte) ((Si[(t7 >>> 24) & 0xFF] & 0xFF) ^ (tt >>> 24));
        result[29] = (byte) ((Si[(t6 >>> 16) & 0xFF] & 0xFF) ^ (tt >>> 16));
        result[30] = (byte) ((Si[(t4 >>> 8) & 0xFF] & 0xFF) ^ (tt >>> 8));
        result[31] = (byte) (Si[t3 & 0xFF] ^ tt);
        if (logger.isTraceEnabled()) {
            logger.trace("PT={}", toString(result));
        }
        trace(OUT, "blockDecrypt()");
    }

    /**
     * A basic symmetric encryption/decryption test for a given key size.
     */
    private static boolean self_test(int keysize) {
        trace(IN, "self_test(" + keysize + ')');
        boolean ok = false;
        try {
            byte[] kb = new byte[keysize];
            byte[] pt = new byte[BLOCK_SIZE];
            int i;

            for (i = 0; i < keysize; i++) {
                kb[i] = (byte) i;
            }
            for (i = 0; i < BLOCK_SIZE; i++) {
                pt[i] = (byte) i;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("==========");
                logger.trace("KEYSIZE={}", 8 * keysize);
                logger.trace("KEY={}", toString(kb));
            }
            Object key = makeKey(kb, BLOCK_SIZE);

            if (logger.isTraceEnabled()) {
                logger.trace("Intermediate Ciphertext Values (Encryption)");
                logger.trace("PT={}", toString(pt));
            }
            byte[] ct = new byte[BLOCK_SIZE];
            blockEncrypt(pt, ct, 0, key, BLOCK_SIZE);

            if (logger.isTraceEnabled()) {
                logger.trace("Intermediate Plaintext Values (Decryption)");
                logger.trace("CT={}", toString(ct));
            }
            byte[] cpt = new byte[BLOCK_SIZE];
            blockDecrypt(ct, cpt, 0, key, BLOCK_SIZE);

            ok = areEqual(pt, cpt);
            if (!ok) {
                throw new RuntimeException("Symmetric operation failed");
            }
        } catch (Exception x) {
            if (logger.isDebugEnabled()) {
                debug("Exception encountered during self-test: " + x.getMessage());
                x.printStackTrace();
            }
        }
        debug("Self-test OK? " + ok);
        trace(OUT, "self_test()");
        return ok;
    }


    //	utility static methods (from cryptix.util.core ArrayUtil and Hex classes)
    //	...........................................................................

    /**
     * Return The number of rounds for a given Rijndael's key and block sizes.
     *
     * @param keySize   The size of the user key material in bytes.
     * @param blockSize The desired block size in bytes.
     *
     * @return The number of rounds for a given Rijndael's key and block sizes.
     */
    private static int getRounds(int keySize, int blockSize) {
        return switch (keySize) {
            case 16 -> switch (blockSize) {
                case 16 -> 10;
                case 24 -> 12;
                default -> 14;
            };
            case 24 -> blockSize != 32 ? 12 : 14;
            default -> // 32 bytes = 256 bits
                14;
        };
    }

    /**
     * Compares two byte arrays for equality.
     *
     * @return true if the arrays have identical contents
     */
    private static boolean areEqual(byte[] a, byte[] b) {
        int aLength = a.length;
        if (aLength != b.length) {
            return false;
        }
        for (int i = 0; i < aLength; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a string of 2 hexadecimal digits (most significant digit first) corresponding to
     * the lowest 8 bits of <i>n</i>.
     */
    private static String byteToString(int n) {
        char[] buf = {HEX_DIGITS[(n >>> 4) & 0x0F], HEX_DIGITS[n & 0x0F]};
        return new String(buf);
    }

    /**
     * Returns a string of 8 hexadecimal digits (most significant digit first) corresponding to
     * the integer <i>n</i>, which is treated as unsigned.
     */
    private static String intToString(int n) {
        char[] buf = new char[8];
        for (int i = 7; i >= 0; i--) {
            buf[i] = HEX_DIGITS[n & 0x0F];
            n >>>= 4;
        }
        return new String(buf);
    }

    /**
     * Returns a string of hexadecimal digits from a byte array. Each byte is converted to 2
     * hex symbols.
     */
    private static String toString(byte[] ba) {
        int length = ba.length;
        char[] buf = new char[length * 2];
        int k;
        int j = 0;
        for (byte b : ba) {
            k = b;
            buf[j++] = HEX_DIGITS[(k >>> 4) & 0x0F];
            buf[j++] = HEX_DIGITS[k & 0x0F];
        }
        return new String(buf);
    }

    /**
     * Returns a string of hexadecimal digits from an integer array. Each int is converted to 4
     * hex symbols.
     */
    private static String toString(int[] ia) {
        int length = ia.length;
        char[] buf = new char[length * 8];
        int k;
        int j = 0;
        for (int value : ia) {
            k = value;
            buf[j++] = HEX_DIGITS[(k >>> 28) & 0x0F];
            buf[j++] = HEX_DIGITS[(k >>> 24) & 0x0F];
            buf[j++] = HEX_DIGITS[(k >>> 20) & 0x0F];
            buf[j++] = HEX_DIGITS[(k >>> 16) & 0x0F];
            buf[j++] = HEX_DIGITS[(k >>> 12) & 0x0F];
            buf[j++] = HEX_DIGITS[(k >>> 8) & 0x0F];
            buf[j++] = HEX_DIGITS[(k >>> 4) & 0x0F];
            buf[j++] = HEX_DIGITS[k & 0x0F];
        }
        return new String(buf);
    }
}
