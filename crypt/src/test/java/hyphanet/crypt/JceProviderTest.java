package hyphanet.crypt;

import hyphanet.support.HexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class JceProviderTest {

    // FIXME I don't think there are any standard test vectors?
    static final byte[] PCFB_256_ENCRYPT_KEY =
        HexUtil.hexToBytes("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4");
    // FIXME This IV was tailored for CTR mode and 128-bit block, maybe needs adjustement
    static final byte[] PCFB_256_ENCRYPT_IV =
        HexUtil.hexToBytes("f0f1f2f3f4f5f6f7f8f9fafbfcfdfefff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");
    // FIXME This plaintext was tailored for 128-bit block, maybe needs adjustement
    static final byte[] PCFB_256_ENCRYPT_PLAINTEXT = HexUtil.hexToBytes(
        "6bc1bee22e409f96e93d7e117393172a" + "ae2d8a571e03ac9c9eb76fac45af8e51" +
        "30c81c46a35ce411e5fbc1191a0a52ef" + "f69f2445df4f9b17ad2b417be66c3710");
    static final byte[] PCFB_256_ENCRYPT_CIPHERTEXT = HexUtil.hexToBytes(
        "c964b00326e216214f1a68f5b0872608" + "1b403c92fe02898664a81f5bbbbf8341" +
        "fc1d04b2c1addfb826cca1eab6813127" + "2751b9d6cd536f78059b10b4867dbbd9");
    static final byte[] PCFB_256_DECRYPT_KEY = PCFB_256_ENCRYPT_KEY;
    static final byte[] PCFB_256_DECRYPT_IV = PCFB_256_ENCRYPT_IV;
    static final byte[] PCFB_256_DECRYPT_PLAINTEXT = PCFB_256_ENCRYPT_PLAINTEXT;
    static final byte[] PCFB_256_DECRYPT_CIPHERTEXT = PCFB_256_ENCRYPT_CIPHERTEXT;

    @BeforeAll
    static void setup() {
        Security.addProvider(new JceProvider());
    }

    @Test
    void testRijndael256KnownValuesRandomLength()
        throws InvalidAlgorithmParameterException, NoSuchPaddingException,
               IllegalBlockSizeException, ShortBufferException, NoSuchAlgorithmException,
               BadPaddingException, InvalidKeyException {
        // Rijndael(256,256)
        checkKnownValuesRandomLength(PCFB_256_ENCRYPT_KEY, PCFB_256_ENCRYPT_IV,
                                     PCFB_256_ENCRYPT_PLAINTEXT, PCFB_256_ENCRYPT_CIPHERTEXT);
        checkKnownValuesRandomLength(PCFB_256_DECRYPT_KEY, PCFB_256_DECRYPT_IV,
                                     PCFB_256_DECRYPT_PLAINTEXT, PCFB_256_DECRYPT_CIPHERTEXT);
    }

    @Test
    void testRijndael256KnownValues() throws NoSuchPaddingException, NoSuchAlgorithmException,
                                             InvalidAlgorithmParameterException,
                                             InvalidKeyException, IllegalBlockSizeException,
                                             BadPaddingException {
        checkKnownValues(PCFB_256_ENCRYPT_KEY, PCFB_256_ENCRYPT_IV, PCFB_256_ENCRYPT_PLAINTEXT,
                         PCFB_256_ENCRYPT_CIPHERTEXT);
        checkKnownValues(PCFB_256_DECRYPT_KEY, PCFB_256_DECRYPT_IV, PCFB_256_DECRYPT_PLAINTEXT,
                         PCFB_256_DECRYPT_CIPHERTEXT);
    }


    @Test
    void testRandom() throws NoSuchPaddingException, NoSuchAlgorithmException,
                             InvalidAlgorithmParameterException, InvalidKeyException,
                             IllegalBlockSizeException, BadPaddingException,
                             ShortBufferException {
        RandomGenerator rng = RandomGenerator.getDefault();

        for (int i = 0; i < 1024; i++) {
            byte[] plaintext = new byte[rng.nextInt(4096) + 1];
            byte[] key = new byte[32];
            byte[] iv = new byte[32];
            rng.nextBytes(plaintext);
            rng.nextBytes(key);
            rng.nextBytes(iv);

            SecretKeySpec k = new SecretKeySpec(key, "Rijndael");
            var params = new IvParameterSpec(iv);
            Cipher c = Cipher.getInstance("RIJNDAEL256/CFB/NoPadding");

            // First encrypt as a block.
            c.init(Cipher.ENCRYPT_MODE, k, params);
            byte[] ciphertext = c.doFinal(plaintext);

            // Now decrypt.
            c.init(Cipher.DECRYPT_MODE, k, params);
            byte[] finalPlaintext = c.doFinal(ciphertext);
            assertArrayEquals(finalPlaintext, plaintext);

            // Now encrypt again, in random pieces.
            c.init(Cipher.ENCRYPT_MODE, k, params);
            byte[] ciphertext2 = new byte[plaintext.length];

            int ptrInput = 0;
            int ptrOutput = 0;
            while (ptrInput < plaintext.length) {
                int count = rng.nextInt(plaintext.length - ptrInput) + 1;
                int stored = c.update(plaintext, ptrInput, count, ciphertext2, ptrOutput);
                ptrInput += count;
                ptrOutput += stored;
            }
            c.doFinal(ciphertext2, ptrOutput);
            assertArrayEquals(ciphertext2, ciphertext);
            // ... and decrypt again, in random pieces.

            byte[] plaintext2 = new byte[ciphertext.length];
            c.init(Cipher.DECRYPT_MODE, k, params);
            ptrInput = 0;
            ptrOutput = 0;
            while (ptrInput < ciphertext.length) {
                int count = rng.nextInt(ciphertext.length - ptrInput) + 1;
                int stored = c.update(ciphertext, ptrInput, count, plaintext2, ptrOutput);
                ptrInput += count;
                ptrOutput += stored;
            }
            c.doFinal(plaintext2, ptrOutput);
            assertArrayEquals(plaintext2, plaintext);

        }
    }


    private void checkKnownValues(byte[] key, byte[] iv, byte[] plaintext, byte[] ciphertext)
        throws NoSuchPaddingException, NoSuchAlgorithmException,
               InvalidAlgorithmParameterException, InvalidKeyException,
               IllegalBlockSizeException, BadPaddingException {

        SecretKeySpec k = new SecretKeySpec(key, "Rijndael");
        var params = new IvParameterSpec(iv);

        Cipher c = Cipher.getInstance("RIJNDAEL256/CFB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k, params);
        byte[] output = c.doFinal(plaintext);
        assertArrayEquals(output, ciphertext);

        c.init(Cipher.DECRYPT_MODE, k, params);
        output = c.doFinal(ciphertext);
        assertArrayEquals(output, plaintext);
    }

    private void checkKnownValuesRandomLength(
        byte[] key, byte[] iv, byte[] plaintext,
        byte[] ciphertext)
        throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException,
               BadPaddingException, ShortBufferException, InvalidAlgorithmParameterException,
               InvalidKeyException {
        for (int i = 0; i < 1024; i++) {
            SecretKeySpec k = new SecretKeySpec(key, "Rijndael");
            var params = new IvParameterSpec(iv);

            RandomGenerator rng = RandomGenerator.getDefault();

            Cipher c = Cipher.getInstance("RIJNDAEL256/CFB/NoPadding");

            byte[] ciphertext2 = new byte[plaintext.length];
            c.init(Cipher.ENCRYPT_MODE, k, params);

            int ptrInput = 0;
            int ptrOutput = 0;
            while (ptrInput < plaintext.length) {
                int count = rng.nextInt(plaintext.length - ptrInput) + 1;
                int stored = c.update(plaintext, ptrInput, count, ciphertext2, ptrOutput);
                ptrInput += count;
                ptrOutput += stored;
            }
            c.doFinal(ciphertext2, ptrOutput);
            assertArrayEquals(ciphertext2, ciphertext);

            byte[] plaintext2 = new byte[ciphertext.length];
            c.init(Cipher.DECRYPT_MODE, k, params);
            ptrInput = 0;
            ptrOutput = 0;
            while (ptrInput < ciphertext.length) {
                int count = rng.nextInt(ciphertext.length - ptrInput) + 1;
                int stored = c.update(ciphertext, ptrInput, count, plaintext2, ptrOutput);
                ptrInput += count;
                ptrOutput += stored;
            }
            c.doFinal(plaintext2, ptrOutput);
            assertArrayEquals(plaintext2, plaintext);
        }
    }


}
