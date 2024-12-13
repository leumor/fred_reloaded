/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt;

/**
 * A symmetric block cipher interface for the Freenet cryptography architecture. This interface
 * defines the standard operations that must be implemented by any block cipher implementation
 * used within the system.
 */
public interface BlockCipher {

    /**
     * Initializes the cipher with the specified key material. This operation typically
     * involves the pre-computation of round keys, S-boxes, or other cipher-specific data
     * structures.
     *
     * @param key the encryption/decryption key material
     */
    void initialize(byte[] key);

    /**
     * Returns the key size supported by this cipher implementation.
     *
     * @return the key size in bits
     */
    int getKeySize();

    /**
     * Returns the block size used by this cipher implementation.
     *
     * @return the block size in bits
     */
    int getBlockSize();

    /**
     * Encrypts a single block of data using this cipher.
     *
     * @param block  the input data block to encrypt. Must be exactly {@code getBlockSize()/8}
     *               bytes long
     * @param result the buffer where the encrypted data will be stored. Must be exactly
     *               {@code getBlockSize()/8} bytes long
     *
     * @implNote The input block may be modified during encryption even when different from
     * result
     */
    void encipher(byte[] block, byte[] result);

    /**
     * Decrypts a single block of data using this cipher.
     *
     * @param block  the input data block to decrypt. Must be exactly {@code getBlockSize()/8}
     *               bytes long
     * @param result the buffer where the decrypted data will be stored. Must be exactly
     *               {@code getBlockSize()/8} bytes long
     *
     * @implNote The input block may be modified during decryption even when different from
     * result
     */
    void decipher(byte[] block, byte[] result);

}
