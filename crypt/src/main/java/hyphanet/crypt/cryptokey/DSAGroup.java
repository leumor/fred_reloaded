/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.cryptokey;

import hyphanet.crypt.CryptFormatException;
import hyphanet.crypt.CryptoKey;
import hyphanet.crypt.Util;
import hyphanet.support.Base64;
import hyphanet.support.HexUtil;
import hyphanet.support.IllegalBase64Exception;
import hyphanet.support.field.FSParseException;
import hyphanet.support.field.SimpleFieldSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.math.BigInteger;

/**
 * Represents the DSA (Digital Signature Algorithm) group parameters used in digital
 * signatures. These parameters are the public values needed for the DSA algorithm
 * implementation.
 * <p>
 * The class encapsulates three primary values:
 * <ul>
 *   <li>p - A large prime modulus</li>
 *   <li>q - A prime divisor of p-1</li>
 *   <li>g - A generator of order q in the multiplicative group of GF(p)</li>
 * </ul>
 * </p>
 *
 * @see CryptoKey
 */
public class DSAGroup implements CryptoKey {
    @Serial
    private static final long serialVersionUID = -1;

    /**
     * Constructs a DSA group with the specified parameters.
     *
     * @param p the prime modulus
     * @param q the prime divisor of p-1
     * @param g the generator
     *
     * @throws IllegalArgumentException if any of the parameters are non-positive
     */
    public DSAGroup(BigInteger p, BigInteger q, BigInteger g) {
        if (p.signum() != 1 || q.signum() != 1 || g.signum() != 1) {
            throw new IllegalArgumentException();
        }
        this.p = p;
        this.q = q;
        this.g = g;
    }

    /**
     * Copy constructor that creates a deep copy of another DSA group.
     *
     * @param group the DSA group to copy
     */
    private DSAGroup(DSAGroup group) {
        this.p = new BigInteger(1, group.p.toByteArray());
        this.q = new BigInteger(1, group.q.toByteArray());
        this.g = new BigInteger(1, group.g.toByteArray());
    }

    /**
     * Reads DSA group parameters from an input stream.
     *
     * @param i the input stream containing the DSA parameters
     *
     * @return a new DSAGroup instance, or the global instance if parameters match
     *
     * @throws IOException          if there's an error reading from the stream
     * @throws CryptFormatException if the parameters are invalid
     */
    public static CryptoKey read(InputStream i) throws IOException, CryptFormatException {
        var p = Util.readMPI(i);
        var q = Util.readMPI(i);
        var g = Util.readMPI(i);

        try {
            DSAGroup group = new DSAGroup(p, q, g);

            return group.equals(Global.DSAgroupBigA) ? Global.DSAgroupBigA : group;
        } catch (IllegalArgumentException e) {
            throw new CryptFormatException("Invalid group: " + e, e);
        }
    }

    /**
     * Creates a DSA group from a SimpleFieldSet containing the parameters.
     *
     * @param fs the SimpleFieldSet containing the DSA parameters
     *
     * @return a new DSAGroup instance, or the global instance if parameters match
     *
     * @throws IllegalBase64Exception if the parameter encoding is invalid
     * @throws FSParseException       if required fields are missing
     */
    public static DSAGroup create(SimpleFieldSet fs)
        throws IllegalBase64Exception, FSParseException {
        var myP = fs.get("p");
        var myQ = fs.get("q");
        var myG = fs.get("g");

        if (myP == null || myQ == null || myG == null) {
            throw new FSParseException("The given SFS doesn't contain required fields!");
        }

        var p = new BigInteger(1, Base64.decode(myP));
        var q = new BigInteger(1, Base64.decode(myQ));
        var g = new BigInteger(1, Base64.decode(myG));
        var dg = new DSAGroup(p, q, g);

        return dg.equals(Global.DSAgroupBigA) ? Global.DSAgroupBigA : dg;
    }

    /**
     * Returns the key type identifier.
     *
     * @return a string in the format "DSA.g-[bitLength]"
     */
    @Override
    public String keyType() {
        return "DSA.g-" + p.bitLength();
    }

    /**
     * Gets the prime modulus p.
     *
     * @return the prime modulus
     */
    public BigInteger getP() {
        return p;
    }

    /**
     * Gets the prime divisor q.
     *
     * @return the prime divisor
     */
    public BigInteger getQ() {
        return q;
    }

    /**
     * Gets the generator g.
     *
     * @return the generator
     */
    public BigInteger getG() {
        return g;
    }

    /**
     * Generates a fingerprint of the DSA group parameters.
     *
     * @return a byte array containing the fingerprint
     */
    @Override
    public byte[] fingerprint() {
        return CryptoKey.fingerprint(new BigInteger[]{p, q, g});
    }

    /**
     * Converts the DSA group parameters to a byte array.
     *
     * @return a byte array containing the serialized parameters
     */
    @Override
    public byte[] asBytes() {
        var pb = Util.calcMPIBytes(p);
        var qb = Util.calcMPIBytes(q);
        var gb = Util.calcMPIBytes(g);
        var tb = new byte[pb.length + qb.length + gb.length];

        System.arraycopy(pb, 0, tb, 0, pb.length);
        System.arraycopy(qb, 0, tb, pb.length, qb.length);
        System.arraycopy(gb, 0, tb, pb.length + qb.length, gb.length);

        return tb;
    }

    /**
     * Checks if this DSA group equals another object.
     *
     * @param o the object to compare with
     *
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        return this == o ||
               (o instanceof DSAGroup other && p.equals(other.p) && q.equals(other.q) &&
                g.equals(other.g));
    }

    /**
     * Checks if this DSA group equals another DSA group.
     *
     * @param o the DSA group to compare with
     *
     * @return true if the groups are equal, false otherwise
     */
    public boolean equals(DSAGroup o) {
        return this == o || (p.equals(o.p) && q.equals(o.q) && g.equals(o.g));
    }

    /**
     * Generates a hash code for this DSA group.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return p.hashCode() ^ q.hashCode() ^ g.hashCode();
    }

    /**
     * Converts the DSA group parameters to a SimpleFieldSet.
     *
     * @return a SimpleFieldSet containing the encoded parameters
     */
    public SimpleFieldSet asFieldSet() {
        var fs = new SimpleFieldSet(true);
        fs.putSingle("p", Base64.encode(p.toByteArray()));
        fs.putSingle("q", Base64.encode(q.toByteArray()));
        fs.putSingle("g", Base64.encode(g.toByteArray()));
        return fs;
    }

    /**
     * Returns a string representation of this DSA group.
     *
     * @return a string representation of the group
     */
    @Override
    public String toString() {
        return this == Global.DSAgroupBigA ? "Global.DSAgroupBigA" : super.toString();
    }

    /**
     * Returns a detailed string representation of this DSA group.
     *
     * @return a detailed string containing all parameters in hexadecimal format
     */
    @Override
    public String toLongString() {
        if (this == Global.DSAgroupBigA) {
            return "Global.DSAgroupBigA";
        }
        return "p=%s, q=%s, g=%s".formatted(HexUtil.biToHex(p), HexUtil.biToHex(q),
                                            HexUtil.biToHex(g));
    }

    /**
     * Creates a copy of this DSA group.
     *
     * @return a new DSAGroup instance with the same parameters
     */
    public DSAGroup cloneKey() {
        return this == Global.DSAgroupBigA ? this : new DSAGroup(this);
    }

    /**
     * The prime modulus
     */
    private final BigInteger p;

    /**
     * The prime divisor of p-1
     */
    private final BigInteger q;

    /**
     * The generator
     */
    private final BigInteger g;

}
