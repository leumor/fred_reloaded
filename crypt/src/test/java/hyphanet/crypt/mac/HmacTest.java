package hyphanet.crypt.mac;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HmacTest {

    // RFC4868 2.7.2.1 SHA256 Authentication Test Vector
    static byte[] plaintext = "Hi There".getBytes();
    static byte[] knownKey =
        Hex.decode("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
    static byte[] knownSHA256 =
        Hex.decode("198a607eb44bfbc69903a0f1cf2bbdc5ba0aa3f3d9ae3c1c7a3b1696a0b68cf7");

    @BeforeEach
    void setUp() {
        random = new Random(0xAAAAAAAAL);
    }

    @Test
    void testAllCipherNames() {
        for (Hmac hmac : Hmac.values()) {
            assertDoesNotThrow(() -> Hmac.mac(hmac, new byte[hmac.digestSize], plaintext));
        }
    }

    @Test
    void testSHA256SignVerify() {
        byte[] key = new byte[32];
        random.nextBytes(key);

        byte[] hmac = Hmac.macWithSHA256(key, plaintext);
        assertNotNull(hmac);
        assertTrue(Hmac.verifyWithSHA256(key, plaintext, hmac));
    }

    @Test
    void testWrongKeySize() {
        byte[] keyTooLong = new byte[31];
        byte[] keyTooShort = new byte[29];
        random.nextBytes(keyTooLong);
        random.nextBytes(keyTooShort);
        try {
            Hmac.macWithSHA256(keyTooLong, plaintext);
            fail();
        } catch (IllegalArgumentException e) {
            // This is expected
        }
        try {
            Hmac.macWithSHA256(keyTooShort, plaintext);
            fail();
        } catch (IllegalArgumentException e) {
            // This is expected
        }
    }

    @Test
    void testKnownVectors() {
        byte[] hmac = Hmac.macWithSHA256(knownKey, plaintext);
        assertEquals(Hex.toHexString(hmac), Hex.toHexString(knownSHA256));
    }

    Random random;
}
