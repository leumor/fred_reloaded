/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.IvParameterSpec;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

class CryptByteBufferTest {
    private static final CryptByteBufferType[] CIPHER_TYPES = CryptByteBufferType.values();

    private static final String IV_PLAIN_TEXT = "6bc1bee22e409f96e93d7e117393172a" +
                                                "ae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52ef" +
                                                "f69f2445df4f9b17ad2b417be66c3710";


    private static final byte[][] KEYS = {
        Hex.decode("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4"),
        Hex.decode("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4"),
        Hex.decode("8c123cffb0297a71ae8388109a6527dd"),
        Hex.decode("a63add96a3d5975e2dad2f904ff584a32920e8aa54263254161362d1fb785790")
    };
    private static final byte[][] IVS = {
        Hex.decode("f0f1f2f3f4f5f6f7f8f9fafbfcfdfefff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"),
        Hex.decode("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"),
        Hex.decode("73c3c8df749084bb"),
        Hex.decode("7b471cf26ee479fb")
    };

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void testSuccessfulRoundTripByteArray() throws GeneralSecurityException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBufferType type = CIPHER_TYPES[i];
            CryptByteBuffer crypt;

            if (IVS[i] == null) {
                crypt = new CryptByteBuffer(type, KEYS[i]);
            } else {
                crypt = new CryptByteBuffer(type, KEYS[i], IVS[i]);
            }
            byte[] decipheredText = crypt.decryptCopy(crypt.encryptCopy(Hex.decode(
                IV_PLAIN_TEXT)));
            assertArrayEquals(
                Hex.decode(IV_PLAIN_TEXT),
                decipheredText,
                "CryptByteBufferType: " + type.name()
            );
        }
    }

    @Test
    void testSuccessfulRoundTripByteArrayNewInstance() throws GeneralSecurityException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBufferType type = CIPHER_TYPES[i];
            CryptByteBuffer crypt;
            byte[] plain = Hex.decode(IV_PLAIN_TEXT);
            if (IVS[i] == null) {
                crypt = new CryptByteBuffer(type, KEYS[i]);
            } else {
                crypt = new CryptByteBuffer(type, KEYS[i], IVS[i]);
            }
            byte[] ciphertext = crypt.encryptCopy(plain);
            byte[] ciphertext2 = crypt.encryptCopy(plain);
            byte[] ciphertext3 = crypt.encryptCopy(plain);

            if (IVS[i] == null) {
                crypt = new CryptByteBuffer(type, KEYS[i]);
            } else {
                crypt = new CryptByteBuffer(type, KEYS[i], IVS[i]);
            }
            byte[] decipheredText = crypt.decryptCopy(ciphertext);
            assertArrayEquals(plain, decipheredText, "CryptByteBufferType: " + type.name());
            decipheredText = crypt.decryptCopy(ciphertext2);
            assertArrayEquals(plain, decipheredText, "CryptByteBufferType2: " + type.name());
            decipheredText = crypt.decryptCopy(ciphertext3);
            assertArrayEquals(plain, decipheredText, "CryptByteBufferType3: " + type.name());
        }
    }

    @Test
    void testEncryptByteArrayNullInput() throws GeneralSecurityException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBufferType type = CIPHER_TYPES[i];
            CryptByteBuffer crypt;
            if (IVS[i] == null) {
                crypt = new CryptByteBuffer(type, KEYS[i]);
            } else {
                crypt = new CryptByteBuffer(type, KEYS[i], IVS[i]);
            }

            assertThrows(
                IllegalArgumentException.class,
                () -> crypt.encryptCopy(null),
                "CryptByteBufferType: " + type.name() + ": Expected NullPointerException"
            );
        }
    }

    @Test
    void testDecryptByteArrayNullInput() throws GeneralSecurityException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBufferType type = CIPHER_TYPES[i];
            CryptByteBuffer crypt;
            if (IVS[i] == null) {
                crypt = new CryptByteBuffer(type, KEYS[i]);
            } else {
                crypt = new CryptByteBuffer(type, KEYS[i], IVS[i]);
            }

            assertThrows(
                IllegalArgumentException.class,
                () -> crypt.decryptCopy(null),
                "CryptByteBufferType: " + type.name() + ": Expected NullPointerException"
            );
        }
    }

    @Test
    void testGetIV() throws InvalidKeyException, InvalidAlgorithmParameterException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBuffer crypt = new CryptByteBuffer(CIPHER_TYPES[i], KEYS[i], IVS[i]);
            assertArrayEquals(crypt.getIV().getIV(), IVS[i]);
        }
    }

    @Test
    void testSetIVIvParameterSpec()
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBuffer crypt = new CryptByteBuffer(CIPHER_TYPES[i], KEYS[i], IVS[i]);
            crypt.genIV();
            crypt.setIV(new IvParameterSpec(IVS[i]));
            assertArrayEquals(IVS[i], crypt.getIV().getIV());
        }
    }

    @Test
    void testSetIVIvParameterSpecNullInput()
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBuffer crypt = new CryptByteBuffer(CIPHER_TYPES[i], KEYS[i], IVS[i]);

            assertThrows(InvalidAlgorithmParameterException.class, () -> crypt.setIV(null));
        }
    }

    @Test
    void testGenIV() throws InvalidKeyException, InvalidAlgorithmParameterException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBuffer crypt = new CryptByteBuffer(CIPHER_TYPES[i], KEYS[i], IVS[i]);
            assertNotNull(crypt.getIV());
        }
    }

    @Test
    void testGenIVLength() throws InvalidKeyException, InvalidAlgorithmParameterException {
        for (int i = 0; i < CIPHER_TYPES.length; i++) {
            CryptByteBuffer crypt = new CryptByteBuffer(CIPHER_TYPES[i], KEYS[i], IVS[i]);
            crypt.genIV();
            assertEquals(crypt.getIV().getIV().length, CIPHER_TYPES[i].ivSize.intValue());
        }
    }

    @BeforeAll
    static void setup() {
        Security.addProvider(new JcaProvider());
    }

}
