/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.cryptokey;

import hyphanet.crypt.CryptoKey;
import hyphanet.crypt.Util;
import hyphanet.support.Base64;
import hyphanet.support.HexUtil;
import hyphanet.support.IllegalBase64Exception;
import hyphanet.support.field.SimpleFieldSet;
import org.apache.commons.rng.UniformRandomProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.math.BigInteger;

/**
 * Represents a DSA private key used for digital signatures in the Hyphanet system. This class
 * implements the private key component of the Digital Signature Algorithm (DSA), storing the
 * private value 'x' and providing methods for key operations.
 *
 * <p>The private key must satisfy the following conditions:
 * <ul>
 *   <li>0 &lt; x &lt; q (where q is the subgroup order from {@link DSAGroup})</li>
 *   <li>x must be a positive number</li>
 * </ul>
 *
 * @see DSAGroup
 * @see CryptoKey
 */
public class DSAPrivateKey implements CryptoKey {
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Constructs a DSA private key with a specified value and group.
     *
     * @param x The private key value
     * @param g The DSA group parameters
     *
     * @throws IllegalArgumentException if the private key value is invalid (not in range 0
     *                                  &lt; x &lt; q)
     */
    public DSAPrivateKey(BigInteger x, DSAGroup g) {
        this.x = x;
        if (x.signum() != 1 || x.compareTo(g.getQ()) > -1 ||
            x.compareTo(BigInteger.ZERO) < 1) {
            throw new IllegalArgumentException("Invalid private key value");
        }
    }

    /**
     * Generates a new random DSA private key within the specified group.
     *
     * @param g   The DSA group parameters
     * @param rng The random provider to use
     */
    public DSAPrivateKey(DSAGroup g, UniformRandomProvider rng) {
        BigInteger q = g.getQ();
        BigInteger tempX;
        do {
            tempX = Util.generateRandomBigInteger(q.bitLength(), rng);
        } while (tempX.compareTo(q) > -1 || tempX.compareTo(BigInteger.ONE) < 0);
        this.x = tempX;
    }

    /**
     * Protected constructor for serialization purposes.
     */
    protected DSAPrivateKey() {
        x = null;
    }

    /**
     * Reads a DSA private key from an input stream.
     *
     * @param i The input stream to read from
     * @param g The DSA group parameters
     *
     * @return A new DSAPrivateKey instance
     *
     * @throws IOException if there is an error reading from the stream
     */
    public static CryptoKey read(InputStream i, DSAGroup g) throws IOException {
        return new DSAPrivateKey(Util.readMPI(i), g);
    }

    /**
     * Creates a DSA private key from a SimpleFieldSet representation.
     *
     * @param fs    The SimpleFieldSet containing the key data
     * @param group The DSA group parameters
     *
     * @return A new DSAPrivateKey instance
     *
     * @throws IllegalBase64Exception if the key data is invalid or appears to be a public key
     */
    public static DSAPrivateKey create(SimpleFieldSet fs, DSAGroup group)
        throws IllegalBase64Exception {
        BigInteger x = new BigInteger(1, Base64.decode(fs.get("x")));
        if (x.bitLength() > 512) {
            throw new IllegalBase64Exception("Probably a pub key");
        }
        return new DSAPrivateKey(x, group);
    }

    /**
     * {@inheritDoc}
     *
     * @return The string "DSA.s" indicating a DSA private key
     */
    @Override
    public String keyType() {
        return "DSA.s";
    }

    /**
     * Returns the private key value x.
     *
     * @return The private key value
     */
    public BigInteger getX() {
        return x;
    }

    /**
     * {@inheritDoc}
     *
     * @return A hexadecimal string representation of the private key
     */
    @Override
    public String toLongString() {
        return "x=" + HexUtil.biToHex(x);
    }

    /**
     * {@inheritDoc}
     *
     * @return The byte representation of the private key value
     */
    @Override
    public byte[] asBytes() {
        return Util.calcMPIBytes(x);
    }

    /**
     * {@inheritDoc}
     *
     * @return An SHA-1 fingerprint of the private key
     */
    @Override
    public byte[] fingerprint() {
        return CryptoKey.fingerprint(new BigInteger[]{x});
    }

    /**
     * Converts the private key to a SimpleFieldSet representation.
     *
     * @return A SimpleFieldSet containing the encoded private key
     */
    public SimpleFieldSet asFieldSet() {
        SimpleFieldSet fs = new SimpleFieldSet(true);
        fs.putSingle("x", Base64.encode(x.toByteArray()));
        return fs;
    }

    /**
     * The private key value x, where 0 &lt; x &lt; q
     */
    private final BigInteger x;
}

