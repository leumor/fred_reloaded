/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.mac;

import hyphanet.base.Fields;
import hyphanet.crypt.exception.UnsupportedTypeException;
import hyphanet.crypt.key.KeyGenUtil;
import org.bouncycastle.crypto.generators.Poly1305KeyGenerator;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;

/**
 * <p>A secure Message Authentication Code (MAC) generator and verifier.</p>
 *
 * <p>This class provides functionality to generate and verify Message Authentication
 * Codes using various algorithms, with primary support for Poly1305AES. It handles both key
 * and IV (Initialization Vector) management for supported algorithms.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * Mac mac = new Mac(MacType.POLY1305_AES);
 * mac.addBytes(data);
 * ByteBuffer macValue = mac.genMac();
 * </pre>
 *
 * @author unixninja92
 * @see MacType
 * @see javax.crypto.Mac
 */
public final class Mac {

    /**
     * <p>Creates a MAC instance with the specified algorithm and key.</p>
     *
     * <p><strong>Note:</strong> This constructor should not be used for algorithms
     * requiring an IV, as a specified key with a random IV is typically not useful for an
     * HMAC.</p>
     *
     * @param type      The MAC algorithm to use
     * @param cryptoKey The secret key for MAC generation
     *
     * @throws InvalidKeyException if the provided key is invalid for the selected algorithm
     * @see MacType
     * @see javax.crypto.SecretKey
     */
    public Mac(MacType type, SecretKey cryptoKey) throws InvalidKeyException {
        this(type, cryptoKey, false, null);
    }

    /**
     * Creates an instance of MessageAuthCode that will use the specified algorithm and key
     * which is converted from a byte[] to a SecretKey. Must not be used on algorithms that
     * require an IV, as a specified key but a random IV is probably not useful for an HMAC.
     *
     * @param type      The MAC algorithm to use
     * @param cryptoKey The key to use
     *
     * @throws InvalidKeyException
     */
    /**
     * <p>Creates a MAC instance with the specified algorithm and key bytes.</p>
     *
     * <p>The provided byte array is converted to a {@link SecretKey} using the
     * appropriate key type for the selected algorithm. This constructor must not be used on
     * algorithms that require an IV, as a specified key but a random IV is probably not useful
     * for an HMAC.</p>
     *
     * @param type      The MAC algorithm to use
     * @param cryptoKey The key bytes to use
     *
     * @throws InvalidKeyException if the key bytes are invalid for the selected algorithm
     * @see KeyGenUtil#getSecretKey
     */
    public Mac(MacType type, byte[] cryptoKey) throws InvalidKeyException {
        this(type, KeyGenUtil.getSecretKey(type.keyType, cryptoKey));
    }

    /**
     * <p>Creates a MAC instance with the specified algorithm and key buffer.</p>
     *
     * <p>The ByteBuffer contents are converted to a {@link SecretKey}. If the algorithm
     * requires an IV, one will be automatically generated.</p>
     *
     * @param type      The MAC algorithm to use
     * @param cryptoKey The key data as a ByteBuffer
     *
     * @throws InvalidKeyException if the key data is invalid for the selected algorithm
     */
    public Mac(MacType type, ByteBuffer cryptoKey) throws InvalidKeyException {
        this(type, Fields.copyToArray(cryptoKey));
    }

    /**
     * <p>Creates a MAC instance with the specified algorithm and generates a new key.</p>
     *
     * <p>This constructor automatically generates both a key and IV (if required by
     * the algorithm).</p>
     *
     * @param type The MAC algorithm to use
     *
     * @throws InvalidKeyException if key generation fails
     * @see KeyGenUtil#genSecretKey
     */
    public Mac(MacType type) throws InvalidKeyException {
        this(type, KeyGenUtil.genSecretKey(type.keyType), true, null);
    }

    /**
     * <p>Creates a MAC instance with the specified algorithm, key, and IV.</p>
     *
     * <p>This constructor should be used for algorithms that require an IV.</p>
     *
     * @param type The MAC algorithm to use
     * @param key  The secret key
     * @param iv   The initialization vector
     *
     * @throws InvalidKeyException      if the key is invalid for the selected algorithm
     * @throws IllegalArgumentException if the algorithm doesn't support IVs
     */
    public Mac(MacType type, SecretKey key, IvParameterSpec iv) throws InvalidKeyException {
        this(type, key, false, iv);
    }

    /**
     * <p>Creates a MAC instance with the specified algorithm, key bytes, and IV.</p>
     *
     * <p>The key bytes are converted to a {@link SecretKey} before use. This constructor
     * should be used for algorithms that require an IV.</p>
     *
     * @param type The MAC algorithm to use
     * @param key  The key as a byte array
     * @param iv   The initialization vector
     *
     * @throws InvalidKeyException if the key is invalid for the selected algorithm
     */
    public Mac(MacType type, byte[] key, IvParameterSpec iv) throws InvalidKeyException {
        this(type, KeyGenUtil.getSecretKey(type.keyType, key), iv);
    }

    /**
     * <p>Internal constructor for MAC initialization with complete parameter control.</p>
     *
     * <p>This constructor handles all initialization scenarios for the MAC engine,
     * including:</p>
     * <ul>
     * <li>Algorithms with and without IV requirements</li>
     * <li>Automatic IV generation when needed</li>
     * <li>Custom IV specification</li>
     * </ul>
     *
     * @param type  The MAC algorithm to use
     * @param key   The secret key for MAC operations
     * @param genIV If true, generates a new IV for algorithms that require one
     * @param iv    The initialization vector to use (can be null if genIV is true or IV not
     *              required)
     *
     * @throws InvalidKeyException      if the key is invalid for the selected algorithm
     * @throws IllegalArgumentException if there's an error initializing the MAC engine with
     *                                  the IV
     */
    private Mac(MacType type, SecretKey key, boolean genIV, IvParameterSpec iv)
        throws InvalidKeyException {
        this.type = type;
        macEngine = type.get();
        this.key = key;
        try {
            if (type.ivLen != -1) {
                checkPoly1305Key(key.getEncoded());
                if (genIV) {
                    genIV();
                } else {
                    setIV(iv);
                }
                macEngine.init(key, this.iv);
            } else {
                macEngine.init(key);
            }
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("Error initializing MAC engine with IV", e);
        }
    }

    /**
     * <p>Verifies the equality of two MAC values.</p>
     *
     * <p>It safely handles null inputs by returning false for any null comparison.</p>
     *
     * @param mac1 the first MAC value to compare
     * @param mac2 the second MAC value to compare
     *
     * @return {@code true} if both MACs are non-null and equal, {@code false} otherwise
     *
     * @see MessageDigest#isEqual(byte[], byte[])
     */
    public static boolean verify(byte[] mac1, byte[] mac2) {
        /*
         * An April 2015 patch prevented null input from throwing. JVMs without that patch will
         * throw, so the change is included here for consistent behavior.
         *
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/rev/10929#l8.13
         */
        if (mac1 == mac2) {
            return true;
        }
        return mac1 != null && mac2 != null && MessageDigest.isEqual(mac1, mac2);
    }

    /**
     * <p>Verifies the equality of two MAC values stored in ByteBuffers.</p>
     *
     * <p><strong>Note:</strong> After this operation, both ByteBuffers will be emptied
     * as their contents are copied for comparison.</p>
     *
     * @param mac1 first MAC value to verify as a ByteBuffer
     * @param mac2 second MAC value to verify as a ByteBuffer
     *
     * @return {@code true} if the MAC values match, {@code false} otherwise
     *
     * @see MessageDigest#isEqual(byte[], byte[])
     * @see Fields#copyToArray(ByteBuffer)
     */
    public static boolean verify(ByteBuffer mac1, ByteBuffer mac2) {
        // Must be constant time, or as close as we can
        return MessageDigest.isEqual(Fields.copyToArray(mac1), Fields.copyToArray(mac2));
    }

    /**
     * <p>Adds a single byte to the MAC computation buffer.</p>
     *
     * <p>This method updates the internal state of the MAC with a single byte.
     * It is useful when processing data byte by byte instead of in arrays.</p>
     *
     * <p>The byte will be included in the final MAC calculation when {@link #genMac()}
     * is called.</p>
     *
     * @param input the byte to add to the MAC computation
     *
     * @throws IllegalStateException if the MAC engine has been closed or is in an invalid
     *                               state
     */
    public void addByte(byte input) {
        macEngine.update(input);
    }

    /**
     * <p>Adds data to the MAC computation.</p>
     *
     * <p>This method updates the internal state of the MAC with the provided bytes.
     * Multiple calls can be made to this method before generating the final MAC.</p>
     *
     * @param input the byte arrays to include in the MAC computation
     *
     * @throws IllegalStateException if the MAC engine has been closed or is in an invalid
     *                               state
     */
    public void addBytes(byte[]... input) {
        for (byte[] b : input) {
            macEngine.update(b);
        }
    }

    /**
     * <p>Adds data from a ByteBuffer to the MAC computation.</p>
     *
     * <p>Reads bytes from the ByteBuffer's current position up to its limit.
     * After this operation, the ByteBuffer's position will be equal to its limit, while the
     * limit remains unchanged.</p>
     *
     * @param input the ByteBuffer containing data to add to the MAC computation
     *
     * @throws IllegalStateException if the MAC engine has been closed or is in an invalid
     *                               state
     */
    public void addBytes(ByteBuffer input) {
        macEngine.update(input);
    }

    /**
     * <p>Adds a portion of a byte array to the MAC computation.</p>
     *
     * <p>This method allows adding a specific segment of a byte array to the MAC
     * calculation, which is useful when working with partial arrays or when processing data in
     * chunks.</p>
     *
     * @param input  the source byte array
     * @param offset the starting position in the array
     * @param len    the number of bytes to include from the offset
     *
     * @throws IllegalStateException if the MAC engine has been closed or is in an invalid
     *                               state
     */
    public void addBytes(byte[] input, int offset, int len) {
        macEngine.update(input, offset, len);
    }

    /**
     * <p>Generates the MAC for all previously added data.</p>
     *
     * <p>This method finalizes the MAC computation and returns the result. The internal
     * buffer is cleared after the MAC is generated.</p>
     *
     * @return a ByteBuffer containing the generated MAC, with a backing array and offset 0
     *
     * @throws IllegalStateException if the MAC engine has been closed or is in an invalid
     *                               state
     */
    public ByteBuffer genMac() {
        return ByteBuffer.wrap(macEngine.doFinal());
    }

    /**
     * <p>Generates a MAC for the specified bytes only.</p>
     *
     * <p>The internal buffer is cleared before and after processing to ensure data isolation.
     * This method is useful when you want to generate a MAC for a single set of data without
     * maintaining state.</p>
     *
     * @param input variable number of byte arrays to generate MAC for
     *
     * @return a ByteBuffer containing the generated MAC
     *
     * @throws IllegalStateException if the MAC engine is in an invalid state
     */
    public ByteBuffer genMac(byte[]... input) {
        macEngine.reset();
        addBytes(input);
        return genMac();
    }

    /**
     * Generates the MAC of only the specified bytes. The buffer is cleared before processing
     * the input to ensure that no extra data is included. Once the MAC has been generated, the
     * buffer is cleared again.
     *
     * @param input
     *
     * @return The Message Authentication Code
     */
    public ByteBuffer genMac(ByteBuffer input) {
        macEngine.reset();
        addBytes(input);
        return genMac();
    }

    /**
     * <p>Verifies the MAC for the provided data against a known MAC value.</p>
     *
     * <p>This method generates a new MAC from the provided data and compares it with
     * the supplied MAC value to see if that MAC is the same as the one passed in. The buffer
     * is cleared before processing the input to ensure that no extra data is included. Once
     * the MAC has been generated, the buffer is cleared again.</p>
     *
     * @param otherMac the MAC value to verify against
     * @param data     the data to generate a MAC from and check the MAC against
     *
     * @return {@code true} if the generated MAC matches the provided MAC
     */
    public boolean verifyData(byte[] otherMac, byte[]... data) {
        return verify(Fields.copyToArray(genMac(data)), otherMac);
    }

    /**
     * <p>Verifies a MAC value against provided data using ByteBuffers.</p>
     *
     * <p>This method generates a MAC from the provided data and compares it with
     * the supplied MAC value.</p>
     *
     * <p><strong>Note:</strong> The buffer is cleared before processing the input
     * to ensure data isolation. After verification, both ByteBuffers will be emptied as their
     * contents are used for comparison.</p>
     *
     * @param otherMac the MAC value to verify against
     * @param data     the data to generate a MAC from
     *
     * @return {@code true} if the generated MAC matches the provided MAC
     *
     * @throws IllegalStateException if the MAC engine is in an invalid state
     */
    public boolean verifyData(ByteBuffer otherMac, ByteBuffer data) {
        return verify(genMac(data), otherMac);
    }

    /**
     * <p>Retrieves the current secret key used for MAC operations.</p>
     *
     * @return the current {@link SecretKey} instance
     */
    public SecretKey getKey() {
        return key;
    }

    /**
     * <p>Retrieves the current Initialization Vector (IV) being used.</p>
     *
     * <p>This method only works with MAC algorithms that support IVs. For algorithms
     * that don't use IVs, this method will throw an exception.</p>
     *
     * @return the current IV as an IvParameterSpec
     *
     * @throws UnsupportedTypeException if the MAC algorithm doesn't support IVs
     */
    public IvParameterSpec getIv() {
        if (type.ivLen == -1) {
            throw new UnsupportedTypeException(type);
        }
        return iv;
    }

    /**
     * <p>Sets a new initialization vector (IV) for algorithms that support it.</p>
     *
     * <p>The MAC engine is reinitialized with the new IV while keeping the same key.</p>
     *
     * @param iv the new IV to use
     *
     * @throws UnsupportedTypeException           if the MAC algorithm doesn't support IVs
     * @throws InvalidAlgorithmParameterException if the provided IV is invalid
     */
    public void setIV(IvParameterSpec iv) throws InvalidAlgorithmParameterException {
        if (type.ivLen == -1) {
            throw new UnsupportedTypeException(type);
        }
        this.iv = iv;
        try {
            macEngine.init(key, iv);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(
                "Error re-initializing MAC engine with new IV",
                                               e
            );
        }
    }

    /**
     * <p>Generates and sets a new Initialization Vector (IV).</p>
     *
     * <p>This method generates a cryptographically secure random IV of the appropriate
     * length for the current MAC algorithm. The new IV is automatically set as the active IV
     * for subsequent MAC operations.</p>
     *
     * @return the newly generated IV
     *
     * @throws UnsupportedTypeException if the MAC algorithm doesn't support IVs
     * @throws IllegalArgumentException if there's an error setting the generated IV
     * @see KeyGenUtil#genIV
     */
    public IvParameterSpec genIV() {
        if (type.ivLen == -1) {
            throw new UnsupportedTypeException(type);
        }
        try {
            setIV(KeyGenUtil.genIV(type.ivLen));
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("Error setting generated IV", e);
        }
        return this.iv;
    }

    /**
     * <p>Validates that a key meets the Poly1305 algorithm requirements.</p>
     *
     * @param encodedKey the key bytes to validate
     *
     * @throws UnsupportedTypeException if the MAC type is not Poly1305_AES
     * @throws IllegalArgumentException if the key is invalid for Poly1305
     */
    private void checkPoly1305Key(byte[] encodedKey) {
        if (type != MacType.POLY1305_AES) {
            throw new UnsupportedTypeException(type);
        }
        Poly1305KeyGenerator.checkKey(encodedKey);
    }

    /**
     * The MAC algorithm type being used
     */
    private final MacType type;

    /**
     * The underlying MAC implementation from javax.crypto
     */
    private final javax.crypto.Mac macEngine;

    /**
     * The secret key used for MAC operations
     */
    private final SecretKey key;

    /**
     * The initialization vector (IV) for algorithms that require it
     */
    private IvParameterSpec iv;
}
