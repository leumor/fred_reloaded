/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.key;

import hyphanet.base.HexUtil;
import hyphanet.crypt.UnsupportedTypeException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static org.junit.jupiter.api.Assertions.*;

class KeyGenUtilTest {
    private static final int TRUE_LENGTH = 16;
    private static final int FALSE_LENGTH = -1;
    private static final KeyType[] KEY_TYPES = KeyType.values();

    private static final byte[][] TRUE_LENGTH_SECRET_KEYS = {
        HexUtil.hexToBytes("20e86dc31ebf2c0e37670e30f8f45c57"),
        HexUtil.hexToBytes("8c6c2e0a60b3b73e9dbef076b68b686bacc9d20081e8822725d14b10b5034f48"),
        HexUtil.hexToBytes("33a4a38b71c8e350d3a98357d1bc9ecd"),
        HexUtil.hexToBytes("be56dbec20bff9f6f343800367287b48c0c28bf47f14b46aad3a32e4f24f0f5e"),
        HexUtil.hexToBytes("2e3e4a8f7c896ebf95fc3a59f283ca1e2808d984ad9043e710f74c4a8f4c8372"),
        HexUtil.hexToBytes("c9f1731f7e996603c6e1f8f72da8a66e51dd8bbc2465f1a9f4d32f800c41ac28" +
                           "f99fe0c1d811678f91300cf33e527436"),
        HexUtil.hexToBytes("2ada39975c02c442e5ebc34832cde05e718acb28e15cdf80c8ab1da9c05bb53c" +
                           "0b026c88a32aee65a924c9ea0b4e6cf5d2d434489d8bb82dfe7876919f690a56"),
        HexUtil.hexToBytes("a92e3fa63e8cbe50869fb352d883911271bf2b0e9048ad04c013b20e901f5806"),
        HexUtil.hexToBytes("45d6c9656b3b115263ba12739e90dcc1"),
        HexUtil.hexToBytes("f468986cbaeecabd4cf242607ac602b51a1adaf4f9a4fc5b298970cbda0b55c6")
    };

    private static final KeyPairType[] TRUE_KEY_PAIR_TYPES =
        {KeyPairType.ECP256, KeyPairType.ECP384, KeyPairType.ECP521};
    @SuppressWarnings("deprecation")
    private static final KeyPairType FALSE_KEY_PAIR_TYPE = KeyPairType.DSA;
    private static final byte[][] TRUE_PUBLIC_KEYS = {
        HexUtil.hexToBytes(
            "3059301306072a8648ce3d020106082a8648ce3d030107034200040126491fbe391419f" +
            "cdca058122a8520a816d3b7af9bc3a3af038e455b311b8234e5915ae2da11550a9f0ff9da5c65257" +
            "c95c2bd3d5c21bcf16f6c15a94a50cb"), HexUtil.hexToBytes(
        "3076301006072a8648ce3d020106052b81040022036200043a095518fc49cfaf6feb5af" +
        "01cf71c02ebfff4fe581d93c6e252c8c607e6568db7267e0b958c4a262a6e6fa7c18572c3af59cd1" +
        "6535a28759d04488bae6c3014bbb4b89c25cbe3b76d7b540dabb13aed5793eb3ce572811b560bb18" +
        "b00a5ac93"), HexUtil.hexToBytes(
        "30819b301006072a8648ce3d020106052b8104002303818600040076083359c8b0b34a9" +
        "03461e435188cb90f7501bcb7ed97e8c506c5b60ff21178a625f80f5729ed4746d8e83b28145a51b" +
        "9495880bf41b8ff0746ea0fe684832cc100ef1b01793c84abf64f31452d95bf0ef43d32440d8bc0d" +
        "67501fcffaf51ae4956e5ff22f3baffea5edddbebbeed0ec3b4af28d18568aaf97b5cd026f675388" +
        "1e0c4")
    };
    private static final byte[][] TRUE_PRIVATE_KEYS = {
        HexUtil.hexToBytes(
            "3041020100301306072a8648ce3d020106082a8648ce3d030107042730250201010420f" +
            "8cb4b29aa51153ba811461e93fd1b2e69a127972f7100c5e246a3b2dcdd1b1c"),
        HexUtil.hexToBytes(
            "304e020100301006072a8648ce3d020106052b81040022043730350201010430b88fe05" +
            "d03b20dca95f19cb0fbabdfef1211452b29527ccac2ea37236d31ab6e7cada08315c62912b5c17cd" +
            "f2d87fa3d"),
        HexUtil.hexToBytes(
            "3060020100301006072a8648ce3d020106052b8104002304493047020101044201b4f57" +
            "3157d51f2e64a8b465fa92e52bae3529270951d448c18e4967beaa04b1f1fedb0e7a1e26f2eefb30" +
            "566a479e1194358670b044fae438d11717eb2a795c3a8")
    };
    private static final byte[] TRUE_IV = new byte[16];
    private static final String KDF_INPUT = "testKey";
    private static final PublicKey[] PUBLIC_KEYS = new PublicKey[TRUE_PUBLIC_KEYS.length];
    private static final PrivateKey[] PRIVATE_KEYS = new PrivateKey[TRUE_PUBLIC_KEYS.length];

    static {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairType type;
        KeyFactory kf;
        X509EncodedKeySpec xks;
        PKCS8EncodedKeySpec pks;
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            try {
                type = TRUE_KEY_PAIR_TYPES[i];
                kf = KeyFactory.getInstance(type.alg);
                xks = new X509EncodedKeySpec(TRUE_PUBLIC_KEYS[i]);
                PUBLIC_KEYS[i] = kf.generatePublic(xks);
                pks = new PKCS8EncodedKeySpec(TRUE_PRIVATE_KEYS[i]);
                PRIVATE_KEYS[i] = kf.generatePrivate(pks);
            } catch (GeneralSecurityException e) {
                throw new Error(e); // Classpath error?
            }
        }
    }

    @Test
    void testGenKeyPair() {
        for (KeyPairType type : TRUE_KEY_PAIR_TYPES) {
            assertNotNull(KeyGenUtil.genKeyPair(type), "KeyPairType: " + type.name());
        }
    }

    @Test
    void testGenKeyPairPublicKeyLength() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPairType type = TRUE_KEY_PAIR_TYPES[i];
            byte[] publicKey = KeyGenUtil.genKeyPair(type).getPublic().getEncoded();
            assertEquals(
                TRUE_PUBLIC_KEYS[i].length,
                publicKey.length,
                "KeyPairType: " + type.name()
            );
        }
    }

    @Test
    void testGenKeyPairDSAType() {
        assertThrows(
            UnsupportedTypeException.class,
            () -> KeyGenUtil.genKeyPair(FALSE_KEY_PAIR_TYPE)
        );
    }

    @Test
    void testGetPublicKey() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPairType type = TRUE_KEY_PAIR_TYPES[i];
            PublicKey key = KeyGenUtil.getPublicKey(type, TRUE_PUBLIC_KEYS[i]);
            assertArrayEquals(
                key.getEncoded(),
                TRUE_PUBLIC_KEYS[i],
                "KeyPairType: " + type.name()
            );
        }
    }

    @Test
    void testGetPublicKeyDSAType() {
        assertThrows(
            UnsupportedTypeException.class,
            () -> KeyGenUtil.getPublicKey(FALSE_KEY_PAIR_TYPE, new byte[0])
        );
    }

    @Test
    void testGetPublicKeyPair() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPairType type = TRUE_KEY_PAIR_TYPES[i];
            KeyPair key = KeyGenUtil.getPublicKeyPair(type, TRUE_PUBLIC_KEYS[i]);
            assertArrayEquals(
                key.getPublic().getEncoded(),
                TRUE_PUBLIC_KEYS[i],
                "KeyPairType: " + type.name()
            );
            assertNull(key.getPrivate(), "KeyPairType: " + type.name());
        }
    }

    @Test
    void testGetPublicKeyPairNotNull() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPairType type = TRUE_KEY_PAIR_TYPES[i];
            assertNotNull(
                KeyGenUtil.getPublicKey(type, TRUE_PUBLIC_KEYS[i]),
                "KeyPairType: " + type.name()
            );
        }
    }

    @Test
    void testGetPublicKeyPairDSAType() {
        assertThrows(
            UnsupportedTypeException.class,
            () -> KeyGenUtil.getPublicKeyPair(FALSE_KEY_PAIR_TYPE, new byte[0])
        );
    }

    @Test
    void testGetKeyPairKeyPairTypeByteArrayByteArray() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPairType type = TRUE_KEY_PAIR_TYPES[i];
            assertNotNull(
                KeyGenUtil.getKeyPair(type, TRUE_PUBLIC_KEYS[i], TRUE_PRIVATE_KEYS[i]),
                "KeyPairType: " + type.name()
            );
        }
    }

    @Test
    void testGetKeyPairKeyPairTypeByteArrayDSAType() {
        byte[] emptyArray = new byte[0];
        assertThrows(
            UnsupportedTypeException.class,
            () -> KeyGenUtil.getKeyPair(FALSE_KEY_PAIR_TYPE, emptyArray, emptyArray)
        );
    }

    @Test
    void testGetKeyPairPublicKeyPrivateKey() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            assertNotNull(
                KeyGenUtil.getKeyPair(PUBLIC_KEYS[i], PRIVATE_KEYS[i]),
                "KeyPairType: " + TRUE_KEY_PAIR_TYPES[i].name()
            );
        }
    }

    @Test
    void testGetKeyPairPublicKeyPrivateKeySamePublic() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPair pair = KeyGenUtil.getKeyPair(PUBLIC_KEYS[i], PRIVATE_KEYS[i]);
            assertEquals(
                pair.getPublic(),
                PUBLIC_KEYS[i],
                "KeyPairType: " + TRUE_KEY_PAIR_TYPES[i].name()
            );
        }
    }

    @Test
    void testGetKeyPairPublicKeyPrivateKeySamePrivate() {
        for (int i = 0; i < TRUE_KEY_PAIR_TYPES.length; i++) {
            KeyPair pair = KeyGenUtil.getKeyPair(PUBLIC_KEYS[i], PRIVATE_KEYS[i]);
            assertEquals(
                pair.getPrivate(),
                PRIVATE_KEYS[i],
                "KeyPairType: " + TRUE_KEY_PAIR_TYPES[i].name()
            );
        }
    }

    @Test
    void testGenSecretKey() {
        for (KeyType type : KEY_TYPES) {
            assertNotNull(KeyGenUtil.genSecretKey(type), "KeyType: " + type.name());
        }
    }

    @Test
    void testGenSecretKeyKeySize() {
        for (KeyType type : KEY_TYPES) {
            byte[] key = KeyGenUtil.genSecretKey(type).getEncoded();
            System.out.println(key.length);
            int keySizeBytes = type.keySize >> 3;
            assertEquals(keySizeBytes, key.length, "KeyType: " + type.name());
        }
    }

    @Test
    void testGetSecretKey() {
        for (int i = 0; i < KEY_TYPES.length; i++) {
            KeyType type = KEY_TYPES[i];
            SecretKey newKey = KeyGenUtil.getSecretKey(type, TRUE_LENGTH_SECRET_KEYS[i]);
            assertArrayEquals(
                TRUE_LENGTH_SECRET_KEYS[i],
                newKey.getEncoded(),
                "KeyType: " + type.name()
            );
        }
    }

    @Test
    void testGenNonceLength() {
        assertEquals(TRUE_LENGTH, KeyGenUtil.genNonce(TRUE_LENGTH).capacity());
    }

    @Test
    void testGenNonceNegativeLength() {
        assertThrows(
            NegativeArraySizeException.class,
            () -> KeyGenUtil.genNonce(FALSE_LENGTH)
        );
    }

    @Test
    void testGenIV() {
        assertEquals(TRUE_LENGTH, KeyGenUtil.genIV(TRUE_LENGTH).getIV().length);
    }

    @Test
    void testGenIVNegativeLength() {
        assertThrows(NegativeArraySizeException.class, () -> KeyGenUtil.genIV(FALSE_LENGTH));
    }

    @Test
    void testGetIvParameterSpecLength() {
        assertEquals(
            TRUE_LENGTH,
            KeyGenUtil.getIvParameterSpec(new byte[16], 0, TRUE_LENGTH).getIV().length
        );
    }

    @Test
    void testGetIvParameterSpecNullInput() {
        assertThrows(
            IllegalArgumentException.class,
            () -> KeyGenUtil.getIvParameterSpec(null, 0, TRUE_IV.length)
        );
    }

    @Test
    void testGetIvParameterSpecOffsetOutOfBounds() {
        assertThrows(
            ArrayIndexOutOfBoundsException.class,
            () -> KeyGenUtil.getIvParameterSpec(TRUE_IV, -4, TRUE_IV.length)
        );
    }

    @Test
    void testGetIvParameterSpecLengthOutOfBounds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> KeyGenUtil.getIvParameterSpec(TRUE_IV, 0, TRUE_IV.length + 20)
        );
    }

    @Test
    void testDeriveSecretKey() throws InvalidKeyException {
        SecretKey kdfKey = KeyGenUtil.getSecretKey(
            KeyType.HMAC_SHA_512,
            TRUE_LENGTH_SECRET_KEYS[6]
        );
        SecretKey buf1 = KeyGenUtil.deriveSecretKey(
            kdfKey,
            KeyGenUtil.class,
            KDF_INPUT,
            KeyType.HMAC_SHA_512
        );
        SecretKey buf2 = KeyGenUtil.deriveSecretKey(
            kdfKey,
            KeyGenUtil.class,
            KDF_INPUT,
            KeyType.HMAC_SHA_512
        );
        assertNotNull(buf1);
        assertEquals(buf1, buf2);
    }

    @Test
    void testDeriveSecretKeyLength() throws InvalidKeyException {
        for (KeyType type : KEY_TYPES) {
            SecretKey kdfKey = KeyGenUtil.getSecretKey(
                KeyType.HMAC_SHA_512,
                TRUE_LENGTH_SECRET_KEYS[6]
            );
            SecretKey buf1 = KeyGenUtil.deriveSecretKey(
                kdfKey,
                KeyGenUtil.class,
                KDF_INPUT,
                type
            );

            assertEquals(buf1.getEncoded().length, type.keySize >> 3);
        }
    }

    @Test
    void testDeriveSecretKeyNullInput1() {
        assertThrows(
            InvalidKeyException.class,
            () -> KeyGenUtil.deriveSecretKey(
                null,
                KeyGenUtil.class,
                KDF_INPUT,
                KeyType.CHACHA_128
            )
        );
    }

    @Test
    void testDeriveIvParameterSpec() throws InvalidKeyException {
        SecretKey kdfKey = KeyGenUtil.getSecretKey(
            KeyType.HMAC_SHA_512,
            TRUE_LENGTH_SECRET_KEYS[6]
        );
        IvParameterSpec buf1 = KeyGenUtil.deriveIvParameterSpec(
            kdfKey,
            KeyGenUtil.class,
            KDF_INPUT,
            KeyType.CHACHA_128
        );
        IvParameterSpec buf2 = KeyGenUtil.deriveIvParameterSpec(
            kdfKey,
            KeyGenUtil.class,
            KDF_INPUT,
            KeyType.CHACHA_128
        );
        assertNotNull(buf1);
        assertArrayEquals(buf1.getIV(), buf2.getIV());
    }

    @Test
    void testDeriveIvParameterSpecLength() throws InvalidKeyException {
        for (KeyType type : KEY_TYPES) {
            SecretKey kdfKey = KeyGenUtil.getSecretKey(
                KeyType.HMAC_SHA_512,
                TRUE_LENGTH_SECRET_KEYS[6]
            );
            IvParameterSpec buf1 = KeyGenUtil.deriveIvParameterSpec(
                kdfKey,
                KeyGenUtil.class,
                KDF_INPUT,
                type
            );

            assertEquals(buf1.getIV().length, type.ivSize >> 3);
        }
    }

    @Test
    void testDeriveIvParameterSpecNullInput1() {
        assertThrows(
            InvalidKeyException.class,
            () -> KeyGenUtil.deriveIvParameterSpec(
                null,
                KeyGenUtil.class,
                KDF_INPUT,
                KeyType.CHACHA_128
            )
        );
    }
}
