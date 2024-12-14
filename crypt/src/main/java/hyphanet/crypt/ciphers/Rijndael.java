package hyphanet.crypt.ciphers;

import hyphanet.crypt.BlockCipher;
import hyphanet.crypt.JceLoader;
import hyphanet.crypt.UnsupportedCipherException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

/*
  This code is part of the Java Adaptive Network Client by Ian Clarke. 
  It is distributed under the GNU Public Licence (GPL) version 2.  See
  http://www.gnu.org/ for further details of the GPL.
 */

/**
 * <p>A Java implementation of the Rijndael (AES) block cipher in CTR mode. This class
 * provides encryption and decryption functionality using the Advanced Encryption Standard
 * algorithm.</p>
 *
 * <p>The implementation supports key sizes of 128, 192, and 256 bits, and block sizes of
 * 128 and 256 bits. It automatically selects the most efficient provider available (either the
 * default JCA provider or BouncyCastle if available).</p>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. The encipher and decipher
 * methods are synchronized to prevent concurrent access to shared state.</p>
 *
 * @author Ian Clarke
 */
public class Rijndael implements BlockCipher {
    /**
     * Logger instance for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Rijndael.class);

    /**
     * The algorithm name and mode used for the cipher initialization. Uses AES in Counter mode
     * without padding.
     */
    private static final String ALGORITHM = "AES/CTR/NOPADDING";

    /**
     * The selected provider for AES-CTR operations. May be null if JCA is restricted to
     * 128-bit keys.
     */
    private static final @Nullable Provider AES_CTR_PROVIDER = getAesCtrProvider();

    /**
     * Creates a new Rijndael cipher instance with specified key and block sizes.
     *
     * @param keySize   The size of the encryption key in bits. Must be 128, 192, or 256.
     * @param blockSize The size of the cipher block in bits. Must be 128 or 256.
     *
     * @throws UnsupportedCipherException if the specified key size or block size is not
     *                                    supported
     */
    public Rijndael(int keySize, int blockSize) throws UnsupportedCipherException {
        if (!((keySize == 128) || (keySize == 192) || (keySize == 256))) {
            throw new UnsupportedCipherException("Invalid keysize: " + keySize);
        }
        if (!((blockSize == 128) || (blockSize == 256))) {
            throw new UnsupportedCipherException("Invalid blocksize: " + blockSize);
        }
        this.keySize = keySize;
        this.blockSize = blockSize;
    }

    /**
     * Default constructor that creates a Rijndael instance with 128-bit key size and 128-bit
     * block size.
     *
     * <p>This constructor is primarily used for reflection-based instantiation.</p>
     */
    public Rijndael() {
        this.keySize = 128;
        this.blockSize = 128;
    }

    /**
     * Creates a new Rijndael cipher instance with specified key size. Block size is set to
     * 128.
     *
     * @param keySize The size of the encryption key in bits. Must be 128, 192, or 256.
     *
     * @throws UnsupportedCipherException if the specified key size is not supported
     */
    public Rijndael(int keySize) throws UnsupportedCipherException {
        this(keySize, 128);
    }

    /**
     * Returns the name of the currently selected cryptographic provider.
     *
     * @return the provider name, or null if using built-in encryption
     */
    public static @Nullable String getProviderName() {
        return AES_CTR_PROVIDER != null ? AES_CTR_PROVIDER.getName() : null;
    }

    /**
     * Returns current cryptographic provider.
     *
     * @return the current provider, or null if using built-in encryption
     */
    public static @Nullable Provider getProvider() {
        return AES_CTR_PROVIDER;
    }

    /**
     * Checks if a cryptographic provider is available for AES operations.
     *
     * @return true if a provider is available, false otherwise
     */
    public static boolean hasProvider() {
        return AES_CTR_PROVIDER != null;
    }

    /**
     * {@inheritDoc}
     *
     * @return the block size in bits
     */
    @Override
    public final int getBlockSize() {
        return blockSize;
    }

    /**
     * {@inheritDoc}
     *
     * @return the key size in bits
     */
    @Override
    public final int getKeySize() {
        return keySize;
    }

    /**
     * Initializes the cipher with the provided key material.
     *
     * @param key the key material to use for initialization
     *
     * @throws IllegalStateException if the key initialization fails
     */
    @Override
    public final void initialize(byte[] key) {
        try {
            byte[] nkey = new byte[keySize >> 3];
            System.arraycopy(key, 0, nkey, 0, nkey.length);
            sessionKey = RijndaelAlgorithm.makeKey(nkey, blockSize / 8);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Failed to initialize key", e);
        }
    }

    /**
     * Encrypts a single block of data.
     *
     * <p>This method is synchronized to ensure thread safety.</p>
     *
     * @param block  the input block to encrypt
     * @param result the array to store the encrypted result
     *
     * @throws IllegalArgumentException if the block size is invalid
     */
    @Override
    public final synchronized void encipher(byte[] block, byte[] result) {
        validateBlockSize(block);
        RijndaelAlgorithm.blockEncrypt(block, result, 0, sessionKey, blockSize / 8);
    }

    /**
     * Decrypts a single block of data.
     *
     * <p>This method is synchronized to ensure thread safety.</p>
     *
     * @param block  the input block to decrypt
     * @param result the array to store the decrypted result
     *
     * @throws IllegalArgumentException if the block size is invalid
     */
    @Override
    public final synchronized void decipher(byte[] block, byte[] result) {
        validateBlockSize(block);
        RijndaelAlgorithm.blockDecrypt(block, result, 0, sessionKey, blockSize / 8);
    }

    /**
     * Validates that the input block has the correct size for encryption/decryption.
     *
     * @param block the input block to validate
     *
     * @throws IllegalArgumentException if the block length does not match the configured block
     *                                  size
     */
    private void validateBlockSize(byte[] block) {
        if (block.length != blockSize / 8) {
            throw new IllegalArgumentException("Invalid block size: " + block.length);
        }
    }

    /**
     * Initializes a cipher instance with a random IV (Initialization Vector) for secure
     * encryption.
     *
     * @param cipher  the cipher instance to initialize
     * @param keySpec the secret key specification to use
     *
     * @throws InvalidAlgorithmParameterException if the algorithm parameters are invalid
     * @throws InvalidKeyException                if the key is invalid
     */
    private static void initCipher(Cipher cipher, SecretKeySpec keySpec)
        throws InvalidAlgorithmParameterException, InvalidKeyException {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(randomBytes));
    }

    /**
     * Performs a benchmark test on the given cipher implementation to measure its
     * performance.
     *
     * <p>The benchmark includes a warm-up phase followed by multiple iterations to find
     * the best performance time.</p>
     *
     * @param cipher the cipher instance to benchmark
     * @param key    the secret key to use for the benchmark
     *
     * @return the best time achieved in nanoseconds
     *
     * @throws GeneralSecurityException if a security-related error occurs during benchmarking
     */
    private static long benchmark(Cipher cipher, SecretKeySpec key)
        throws GeneralSecurityException {
        long bestTime = Long.MAX_VALUE;
        byte[] input = new byte[1024];
        byte[] output = new byte[input.length * 32];

        // Warm-up phase
        initCipher(cipher, key);
        for (int i = 0; i < 32; i++) {
            cipher.doFinal(input);
            System.arraycopy(output, 0, input, 0, input.length);
        }

        // Benchmark phase
        for (int i = 0; i < 128; i++) {
            long startTime = System.nanoTime();
            initCipher(cipher, key);

            try {
                for (int j = 0; j < 4; j++) {
                    int offset = 0;
                    for (int k = 0; k < 32; k++) {
                        offset += cipher.update(input, 0, input.length, output, offset);
                    }
                    cipher.doFinal(output, offset);
                }

                bestTime = Math.min(System.nanoTime() - startTime, bestTime);
                System.arraycopy(output, 0, input, 0, input.length);
            } catch (GeneralSecurityException e) {
                logger.warn("Benchmark iteration failed", e);
            }
        }
        return bestTime;
    }

    /**
     * Selects the best performing provider between the default JCA provider and BouncyCastle
     * based on benchmark results.
     *
     * @param keySpec the secret key specification to use for testing
     *
     * @return the best performing provider
     *
     * @throws GeneralSecurityException if provider initialization fails
     */
    private static Provider selectBestProvider(SecretKeySpec keySpec)
        throws GeneralSecurityException {
        Cipher defaultCipher = Cipher.getInstance(ALGORITHM);
        initCipher(defaultCipher, keySpec);
        Provider defaultProvider = defaultCipher.getProvider();

        Provider bouncyCastle = JceLoader.getBouncyCastle();
        if (bouncyCastle == null) {
            return defaultProvider;
        }

        try {
            Cipher bcCipher = Cipher.getInstance(ALGORITHM, bouncyCastle);
            initCipher(bcCipher, keySpec);

            long defaultTime = benchmark(defaultCipher, keySpec);
            long bcTime = benchmark(bcCipher, keySpec);

            logger.debug("{}/{}: {}ns", ALGORITHM, defaultProvider, defaultTime);
            logger.debug("{}/{}: {}ns", ALGORITHM, bouncyCastle, bcTime);

            return bcTime < defaultTime ? bouncyCastle : defaultProvider;
        } catch (GeneralSecurityException e) {
            logger.warn("BouncyCastle benchmark failed, using default provider", e);
            return defaultProvider;
        }
    }

    /**
     * Determines the appropriate AES-CTR provider for the system.
     *
     * <p>This method tests whether the system supports 256-bit keys and selects
     * the best performing provider. If the JCA is restricted to 128-bit keys, it returns null
     * to indicate that the built-in encryption should be used.</p>
     *
     * @return the selected provider, or null if JCA is restricted to 128-bit keys
     */
    @SuppressWarnings("java:S1168")
    private static Provider getAesCtrProvider() {
        try {
            byte[] key = new byte[32]; // Test for whether 256-bit works.

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

            Provider selectedProvider = selectBestProvider(keySpec);
            logger.info("Using JCA provider: {}", selectedProvider);
            return selectedProvider;
        } catch (GeneralSecurityException e) {
            logger.warn(
                "JCA initialization failed (restricted to 128-bit keys). Using built-in " +
                "encryption.", e);
            return null;
        }
    }

    /**
     * The size of the encryption key in bits
     */
    private final int keySize;

    /**
     * The size of the cipher block in bits
     */
    private final int blockSize;

    /**
     * The session key object used for encryption/decryption operations
     */
    private Object sessionKey;
}
