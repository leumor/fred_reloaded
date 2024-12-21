package hyphanet.crypt;

import hyphanet.support.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;


class UtilTest {


    @Test
    void fillByteArrayFromInts() {
        int[] ints = {1, 2, 3, 4};
        byte[] bytes = new byte[ints.length * 4];
        Util.fillByteArrayFromInts(ints, bytes);
        byte[] expectedBytes = {0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4};
        assertArrayEquals(expectedBytes, bytes);

        int[] largeInts = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        byte[] largeBytes = new byte[largeInts.length * 4];
        Util.fillByteArrayFromInts(largeInts, largeBytes);
        byte[] expectedLargeBytes =
            {(byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x80, (byte) 0x00,
                (byte) 0x00, (byte) 0x00};
        assertArrayEquals(expectedLargeBytes, largeBytes);

        int[] zeroInts = {0, 0, 0};
        byte[] zeroBytes = new byte[zeroInts.length * 4];
        Util.fillByteArrayFromInts(zeroInts, zeroBytes);
        byte[] expectedZeroBytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(expectedZeroBytes, zeroBytes);
    }

    @Test
    void fillByteArrayFromLongs() {
        long[] longs = {1L, 2L, 3L, 4L};
        byte[] bytes = new byte[longs.length * 8];
        Util.fillByteArrayFromLongs(longs, bytes);
        byte[] expectedBytes =
            {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0,
                0, 0, 0, 0, 4};
        assertArrayEquals(expectedBytes, bytes);

        long[] largeLongs = {Long.MAX_VALUE, Long.MIN_VALUE};
        byte[] largeBytes = new byte[largeLongs.length * 8];
        Util.fillByteArrayFromLongs(largeLongs, largeBytes);
        byte[] expectedLargeBytes =
            {(byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        assertArrayEquals(expectedLargeBytes, largeBytes);


        long[] zeroLongs = {0L, 0L};
        byte[] zeroBytes = new byte[zeroLongs.length * 8];
        Util.fillByteArrayFromLongs(zeroLongs, zeroBytes);
        byte[] expectedZeroBytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertArrayEquals(expectedZeroBytes, zeroBytes);
    }

    @Test
    void calcMPIBytes() {
        BigInteger num1 = new BigInteger("9");
        byte[] mpiBytes1 = Util.calcMPIBytes(num1);
        byte[] expectedMpiBytes1 = {0, 4, 9};
        assertArrayEquals(expectedMpiBytes1, mpiBytes1);


        BigInteger num2 = new BigInteger("1234567890123456789");
        byte[] mpiBytes2 = Util.calcMPIBytes(num2);
        byte[] expectedMpiBytes2 = {0, 61, 17, 34, 16, -12, 125, -23, -127, 21};

        assertArrayEquals(expectedMpiBytes2, mpiBytes2);

        BigInteger num3 = new BigInteger("100200300400500600700800900");
        byte[] mpiBytes3 = Util.calcMPIBytes(num3);
        byte[] expectedMpiBytes3 = {0, 87, 82, -30, 61, 43, 60, -57, 29, -97, 74, 15, -124};
        assertArrayEquals(expectedMpiBytes3, mpiBytes3);

        BigInteger zero = BigInteger.ZERO;
        byte[] mpiBytesZero = Util.calcMPIBytes(zero);
        byte[] expectedZeroBytes = {0, 0};
        assertArrayEquals(expectedZeroBytes, mpiBytesZero);
    }


    @Test
    void writeMPI() throws IOException {
        BigInteger num1 = new BigInteger("9");
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        Util.writeMPI(num1, out1);
        byte[] expectedBytes1 = {0, 4, 9};
        assertArrayEquals(expectedBytes1, out1.toByteArray());

        BigInteger num2 = new BigInteger("1234567890123456789");
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        Util.writeMPI(num2, out2);
        byte[] expectedBytes2 = {0, 61, 17, 34, 16, -12, 125, -23, -127, 21};

        assertArrayEquals(expectedBytes2, out2.toByteArray());

        BigInteger num3 = new BigInteger("100200300400500600700800900");
        ByteArrayOutputStream out3 = new ByteArrayOutputStream();
        Util.writeMPI(num3, out3);
        byte[] expectedBytes3 = {0, 87, 82, -30, 61, 43, 60, -57, 29, -97, 74, 15, -124};

        assertArrayEquals(expectedBytes3, out3.toByteArray());

        BigInteger zero = BigInteger.ZERO;
        ByteArrayOutputStream out4 = new ByteArrayOutputStream();
        Util.writeMPI(zero, out4);
        byte[] expectedBytes4 = {0, 0};
        assertArrayEquals(expectedBytes4, out4.toByteArray());


    }

    @Test
    void readMPI() throws IOException {
        byte[] mpiBytes1 = {0, 4, 9};
        ByteArrayInputStream in1 = new ByteArrayInputStream(mpiBytes1);
        BigInteger result1 = Util.readMPI(in1);
        assertEquals(new BigInteger("9"), result1);

        byte[] mpiBytes2 = {0, 61, 17, 34, 16, -12, 125, -23, -127, 21};
        ByteArrayInputStream in2 = new ByteArrayInputStream(mpiBytes2);
        BigInteger result2 = Util.readMPI(in2);
        assertEquals(new BigInteger("1234567890123456789"), result2);

        byte[] mpiBytes3 = {0, 87, 82, -30, 61, 43, 60, -57, 29, -97, 74, 15, -124};
        ByteArrayInputStream in3 = new ByteArrayInputStream(mpiBytes3);
        BigInteger result3 = Util.readMPI(in3);
        assertEquals(new BigInteger("100200300400500600700800900"), result3);

        byte[] mpiBytes4 = {0, 0};
        ByteArrayInputStream in4 = new ByteArrayInputStream(mpiBytes4);
        BigInteger result4 = Util.readMPI(in4);
        assertEquals(BigInteger.ZERO, result4);

        byte[] shortMpiBytes = {0, 2};
        ByteArrayInputStream shortIn = new ByteArrayInputStream(shortMpiBytes);
        assertThrows(EOFException.class, () -> Util.readMPI(shortIn));
    }


    @Test
    void hashBytes() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        byte[] sha1Hash = Util.hashBytes(Util.HashAlgorithm.SHA1, data);
        byte[] expectedSha1Hash =
            HexUtil.hexToBytes("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3");
        assertArrayEquals(expectedSha1Hash, sha1Hash);

        byte[] md5Hash = Util.hashBytes(Util.HashAlgorithm.MD5, data);
        byte[] expectedMd5Hash = HexUtil.hexToBytes("098f6bcd4621d373cade4e832627b4f6");
        assertArrayEquals(expectedMd5Hash, md5Hash);


        byte[] sha256Hash = Util.hashBytes(Util.HashAlgorithm.SHA256, data);
        byte[] expectedSha256Hash = HexUtil.hexToBytes(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        assertArrayEquals(expectedSha256Hash, sha256Hash);

        byte[] sha384Hash = Util.hashBytes(Util.HashAlgorithm.SHA384, data);
        byte[] expectedSha384Hash = HexUtil.hexToBytes(
            "768412320f7b0aa5812fce428dc4706b3cae50e02a64caa16a782249bfe8efc4b7ef1ccb126255d196047dfedf17a0a9");
        assertArrayEquals(expectedSha384Hash, sha384Hash);

        byte[] sha512Hash = Util.hashBytes(Util.HashAlgorithm.SHA512, data);
        byte[] expectedSha512Hash = HexUtil.hexToBytes(
            "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff");
        assertArrayEquals(expectedSha512Hash, sha512Hash);


        byte[] data2 = "testing".getBytes(StandardCharsets.UTF_8);
        byte[] offsetSha256Hash =
            Util.hashBytes(Util.HashAlgorithm.SHA256, data2, 1, data2.length - 1);
        byte[] expectedOffsetSha256Hash = Util.hashBytes(Util.HashAlgorithm.SHA256,
                                                         "esting".getBytes(
                                                             StandardCharsets.UTF_8));
        assertArrayEquals(expectedOffsetSha256Hash, offsetSha256Hash);
    }

    @Test
    void hashString() {
        String testString = "test";
        byte[] sha1Hash = Util.hashString(Util.HashAlgorithm.SHA1, testString);
        byte[] expectedSha1Hash =
            HexUtil.hexToBytes("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3");
        assertArrayEquals(expectedSha1Hash, sha1Hash);

        byte[] md5Hash = Util.hashString(Util.HashAlgorithm.MD5, testString);
        byte[] expectedMd5Hash = HexUtil.hexToBytes("098f6bcd4621d373cade4e832627b4f6");
        assertArrayEquals(expectedMd5Hash, md5Hash);


        byte[] sha256Hash = Util.hashString(Util.HashAlgorithm.SHA256, testString);
        byte[] expectedSha256Hash = HexUtil.hexToBytes(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        assertArrayEquals(expectedSha256Hash, sha256Hash);

        byte[] sha384Hash = Util.hashString(Util.HashAlgorithm.SHA384, testString);
        byte[] expectedSha384Hash = HexUtil.hexToBytes(
            "768412320f7b0aa5812fce428dc4706b3cae50e02a64caa16a782249bfe8efc4b7ef1ccb126255d196047dfedf17a0a9");
        assertArrayEquals(expectedSha384Hash, sha384Hash);

        byte[] sha512Hash = Util.hashString(Util.HashAlgorithm.SHA512, testString);
        byte[] expectedSha512Hash = HexUtil.hexToBytes(
            "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff");
        assertArrayEquals(expectedSha512Hash, sha512Hash);
    }


    @Test
    void xor() {
        byte[] b1 = {1, 2, 3};
        byte[] b2 = {3, 2, 1};
        byte[] expectedXor = {2, 0, 2};
        assertArrayEquals(expectedXor, Util.xor(b1, b2));


        byte[] b3 = {1, 2};
        byte[] b4 = {3, 2, 1};
        byte[] expectedXor2 = {2, 0, 0};
        assertArrayEquals(expectedXor2, Util.xor(b3, b4));


        byte[] b5 = {1, 2, 3};
        byte[] b6 = {1, 2, 3};
        byte[] expectedXor3 = {0, 0, 0};
        assertArrayEquals(expectedXor3, Util.xor(b5, b6));

    }

    @Test
    void makeKey() {
        byte[] entropy = new byte[32];
        new Random().nextBytes(entropy);

        byte[] key = new byte[16];
        Util.makeKey(entropy, key, 0, 16);
        assertNotEquals(0, key[0]); //ensure key has some value, its not all 0s after key
        // generation
        byte[] allZeros = new byte[entropy.length];
        assertArrayEquals(allZeros, entropy); //ensure entropy is zeroed out


        byte[] key2 = new byte[32];
        byte[] entropy2 = new byte[64];
        new Random().nextBytes(entropy2);
        Util.makeKey(entropy2, key2, 0, 32);
        assertNotEquals(0, key2[0]);
        byte[] allZeros2 = new byte[entropy2.length];
        assertArrayEquals(allZeros2, entropy2); //ensure entropy is zeroed out


        byte[] key3 = new byte[8];
        byte[] entropy3 = new byte[16];
        new Random().nextBytes(entropy3);
        Util.makeKey(entropy3, key3, 0, 8);
        assertNotEquals(0, key3[0]);
        byte[] allZeros3 = new byte[entropy3.length];
        assertArrayEquals(allZeros3, entropy3); //ensure entropy is zeroed out
    }

    @Test
    void log2() {
        assertEquals(0, Util.log2(1));
        assertEquals(1, Util.log2(2));
        assertEquals(2, Util.log2(3));
        assertEquals(2, Util.log2(4));
        assertEquals(3, Util.log2(8));
        assertEquals(4, Util.log2(16));
        assertEquals(6, Util.log2(64));
        assertEquals(7, Util.log2(128));
        assertEquals(10, Util.log2(1024));
        assertEquals(31, Util.log2(Integer.MAX_VALUE));
        assertEquals(63, Util.log2(Long.MAX_VALUE));

    }

    @Test
    void readFully() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        byte[] buffer = new byte[3];
        Util.readFully(in, buffer);
        byte[] expectedBuffer = {1, 2, 3};
        assertArrayEquals(expectedBuffer, buffer);

        byte[] buffer2 = new byte[2];
        Util.readFully(in, buffer2);
        byte[] expectedBuffer2 = {4, 5};
        assertArrayEquals(expectedBuffer2, buffer2);


        byte[] shortData = {1, 2};
        ByteArrayInputStream shortIn = new ByteArrayInputStream(shortData);
        byte[] shortBuffer = new byte[3];
        assertThrows(EOFException.class, () -> Util.readFully(shortIn, shortBuffer));


        byte[] data3 = {1, 2, 3, 4, 5, 6};
        ByteArrayInputStream in3 = new ByteArrayInputStream(data3);
        byte[] buffer3 = new byte[3];
        Util.readFully(in3, buffer3, 0, 3);
        byte[] expectedBuffer3 = {1, 2, 3};
        assertArrayEquals(expectedBuffer3, buffer3);

        byte[] buffer4 = new byte[3];
        Util.readFully(in3, buffer4, 0, 3);
        byte[] expectedBuffer4 = {4, 5, 6};
        assertArrayEquals(expectedBuffer4, buffer4);
    }


    @Test
    void keyDigestAsNormalizedDouble() {
        byte[] digest = {0, 0, 0, 0, 0, 0, 0, 1};
        double result1 = Util.keyDigestAsNormalizedDouble(digest);
        assertEquals(0.0078125, result1, 0.000001);


        byte[] digest2 = {0, 0, 0, 0, 0, 0, 0, -1};
        double result2 = Util.keyDigestAsNormalizedDouble(digest2);
        assertEquals(0.0078125, result2, 0.000001);


        byte[] digest3 = {0, 0, 0, 0, 0, 0, 0, 0};
        double result3 = Util.keyDigestAsNormalizedDouble(digest3);
        assertEquals(0.0, result3, 0.000001);


        byte[] digest4 = {-1, -1, -1, -1, -1, -1, -1, -1};
        double result4 = Util.keyDigestAsNormalizedDouble(digest4);
        assertEquals(1.0 / Long.MAX_VALUE, result4, 0.000001);

        byte[] digest5 = {-128, 0, 0, 0, 0, 0, 0, 0};
        double result5 = Util.keyDigestAsNormalizedDouble(digest5);
        assertEquals(128.0 / Long.MAX_VALUE, result5, 0.000001);
    }

}