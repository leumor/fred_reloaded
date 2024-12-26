/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.mac;

import hyphanet.base.Fields;
import hyphanet.crypt.UnsupportedTypeException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

class MacTest {
    private static final MacType[] TYPES = MacType.values();
    private static final byte[][] KEYS =
        {Hex.decode("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            Hex.decode("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            Hex.decode("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            Hex.decode("e285000e6080a701a410040f4814470b568d149b821f99d41319e6410094a760")};
    private static final byte[] HMAC_MESSAGE = "Hi There".getBytes(StandardCharsets.UTF_8);
    private static final byte[][] MESSAGES =
        {HMAC_MESSAGE, HMAC_MESSAGE, HMAC_MESSAGE, Hex.decode("66f75c0e0c7a406586")};
    private static final IvParameterSpec[] IVS = {null, null, null,
        new IvParameterSpec(Hex.decode("166450152e2394835606a9d1dd2cdc8b"))};
    private static final byte[][] TRUE_MACS =
        {Hex.decode("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"),
            Hex.decode(
                "afd03944d84895626b0825f4ab46907f15f9dadbe4101ec682aa034c7cebc59cfaea9ea9076ede7" +
                "f4af152e8b2fa9cb6"), Hex.decode(
            "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cdedaa833b7d6b8a70" +
            "2038b274eaea3f4e4be9d914eeb61f1702e696c203a126854"),
            Hex.decode("1644272eee3b30b7f82568425e817756")};
    private static final byte[][] FALSE_MACS =
        {Hex.decode("4bb5e21dd13001ed5faccfcfdaf8a854881dc200c9833da726e9376c2e32cff7"),
            Hex.decode(
                "4bb5e21dd13001ed5faccfcfdaf8a854881dc200c9833da726e9376c2e32cff7faea9ea9076ede7" +
                "f4af152e8b2fa9cb6"), Hex.decode(
            "4bb5e21dd13001ed5faccfcfdaf8a854881dc200c9833da726e9376c2e32cff7faea9ea9076ede7" +
            "2038b274eaea3f4e4be9d914eeb61f1702e696c203a126854"),
            Hex.decode("881dc200c9833da726e9376c2e32cff7")};

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void testAddByte() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }

            for (int j = 0; j < MESSAGES[i].length; j++) {
                mac.addByte(MESSAGES[i][j]);
            }
            assertArrayEquals(
                Fields.copyToArray(mac.genMac()),
                TRUE_MACS[i],
                "MacType: " + TYPES[i].name()
            );
        }
    }

    @Test
    void testAddBytesByteBuffer() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(MESSAGES[i]);

            mac.addBytes(byteBuffer);
            assertArrayEquals(
                mac.genMac().array(),
                TRUE_MACS[i],
                "MacType: " + TYPES[i].name()
            );
        }
    }

    @Test
    void testAddBytesByteArrayIntInt() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            mac.addBytes(MESSAGES[i], 0, MESSAGES[i].length / 2);
            mac.addBytes(
                MESSAGES[i],
                MESSAGES[i].length / 2,
                MESSAGES[i].length - MESSAGES[i].length / 2
            );

            assertArrayEquals(
                mac.genMac().array(),
                TRUE_MACS[i],
                "MacType: " + TYPES[i].name()
            );
        }
    }

    @Test
    void testAddBytesByteArrayIntIntOffsetOutOfBounds() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }

            boolean throwNull = false;
            try {
                mac.addBytes(MESSAGES[i], -3, MESSAGES[i].length - 3);
            } catch (IllegalArgumentException e) {
                throwNull = true;
            }

            assertTrue(throwNull, "MacType: " + TYPES[i].name());
        }
    }

    @Test
    void testAddBytesByteArrayIntIntLengthOutOfBounds() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }

            boolean throwNull = false;
            try {
                mac.addBytes(MESSAGES[i], 0, MESSAGES[i].length + 3);
            } catch (IllegalArgumentException e) {
                throwNull = true;
            }

            assertTrue(throwNull, "MacType: " + TYPES[i].name());
        }
    }

    @Test
        //tests .genMac() and .addBytes(byte[]...] as well
    void testGetMacByteArrayArray() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            byte[] result = mac.genMac(MESSAGES[i]).array();
            assertTrue(Mac.verify(result, TRUE_MACS[i]), "MacType: " + TYPES[i].name());
        }
    }

    @Test
    void testGetMacByteArrayArrayReset() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            mac.addBytes(MESSAGES[i]);
            byte[] result = mac.genMac(MESSAGES[i]).array();
            assertArrayEquals(TRUE_MACS[i], result, "MacType: " + TYPES[i].name());
        }
    }

    @Test
    void testVerify() {
        assertTrue(Mac.verify(TRUE_MACS[3], TRUE_MACS[3]));
    }

    @Test
    void testVerifyFalse() {
        assertFalse(Mac.verify(TRUE_MACS[3], FALSE_MACS[3]));
    }

    @Test
    void testVerifyNullInput1() {
        assertFalse(Mac.verify(null, TRUE_MACS[3]));
    }

    @Test
    void testVerifyNullInput2() {
        assertFalse(Mac.verify(TRUE_MACS[1], null));
    }

    @Test
    void testVerifyData() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            System.out.println(TYPES[i].name());
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            assertTrue(
                mac.verifyData(TRUE_MACS[i], MESSAGES[i]),
                "MacType: " + TYPES[i].name()
            );
        }
    }

    @Test
    void testVerifyDataFalse() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            assertFalse(
                mac.verifyData(FALSE_MACS[i], MESSAGES[i]),
                "MacType: " + TYPES[i].name()
            );
        }
    }

    @Test
    void testVerifyDataNullInput1() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            assertFalse(mac.verifyData(null, MESSAGES[i]), "MacType: " + TYPES[i].name());
        }
    }

    @Test
    void testGetKey() throws InvalidKeyException {
        for (int i = 0; i < TYPES.length; i++) {
            Mac mac;
            if (TYPES[i].ivLen != -1) {
                mac = new Mac(TYPES[i], KEYS[i], IVS[i]);
            } else {
                mac = new Mac(TYPES[i], KEYS[i]);
            }
            assertArrayEquals(
                KEYS[i],
                mac.getKey().getEncoded(),
                "MacType: " + TYPES[i].name()
            );
        }
    }

    @Test
    void testGetIV() throws InvalidKeyException {
        Mac mac = new Mac(TYPES[3], KEYS[3], IVS[3]);
        assertArrayEquals(mac.getIv().getIV(), IVS[3].getIV());
    }

    @Test
    void testGetIVUnsupportedTypeException() throws InvalidKeyException {
        Mac mac = new Mac(TYPES[0], KEYS[0]);
        assertThrows(UnsupportedTypeException.class, mac::getIv);
    }

    @Test
    void testSetIVIvParameterSpec()
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        Mac mac = new Mac(TYPES[3], KEYS[3], IVS[3]);
        mac.genIV();
        mac.setIV(IVS[3]);
        assertArrayEquals(IVS[3].getIV(), mac.getIv().getIV());
    }

    @Test
    void testSetIVIvParameterSpecNullInput() {

        assertThrows(
            InvalidAlgorithmParameterException.class, () -> {
                Mac mac = new Mac(TYPES[3], KEYS[3], IVS[3]);
                mac.setIV(null);
            }
        );
    }

    @Test
    void testSetIVIvParameterSpecUnsupportedTypeException() throws InvalidKeyException {
        Mac mac = new Mac(TYPES[0], KEYS[0]);
        assertThrows(UnsupportedTypeException.class, () -> mac.setIV(null));
    }

    @Test
    void testGenIV() throws InvalidKeyException {
        Mac mac = new Mac(TYPES[3], KEYS[3], IVS[3]);
        assertNotNull(mac.genIV());
    }

    @Test
    void testGenIVLength() throws InvalidKeyException {
        Mac mac = new Mac(TYPES[3], KEYS[3], IVS[3]);
        assertEquals(mac.genIV().getIV().length, TYPES[3].ivLen);
    }

    @Test
    void testGenIVUnsupportedTypeException() throws InvalidKeyException {
        Mac mac = new Mac(TYPES[0], KEYS[0]);
        assertThrows(UnsupportedTypeException.class, mac::genIV);
    }
}
