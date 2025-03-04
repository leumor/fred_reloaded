/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.crypt.key;

import java.security.spec.ECGenParameterSpec;

/**
 * An enumeration of supported key pair types for cryptographic operations in Hyphanet. This enum
 * maintains the specifications for various elliptic curve algorithms and legacy DSA support.
 *
 * <p>Each key pair type defines:
 *
 * <ul>
 *   <li>The cryptographic algorithm to use
 *   <li>The curve specification for EC-based algorithms
 *   <li>The expected size of DER-encoded public keys
 * </ul>
 *
 * @author unixninja92
 */
public enum KeyPairType {
  /** NIST P-256 elliptic curve (secp256r1). Provides 128-bit security level. */
  ECP256("EC", "secp256r1", 91),

  /** NIST P-384 elliptic curve (secp384r1). Provides 192-bit security level. */
  ECP384("EC", "secp384r1", 120),

  /** NIST P-521 elliptic curve (secp521r1). Provides 256-bit security level. */
  ECP521("EC", "secp521r1", 158);

  /** The cryptographic algorithm identifier */
  public final String alg;

  /** The name of the curve specification */
  public final String specName;

  /** The expected size of a DER-encoded public key in bytes. */
  public final int modulusSize;

  /**
   * The elliptic curve parameter specification. This is null for DSA type. ECGenParameterSpec is
   * immutable.
   */
  @SuppressWarnings("ImmutableEnumChecker")
  public final ECGenParameterSpec spec;

  /**
   * Constructs the DSA enum value with default parameters. This constructor is used only for the
   * legacy DSA type.
   */
  KeyPairType() {
    alg = name();
    specName = alg;
    modulusSize = 128;
    spec = null;
  }

  /**
   * Constructs an EC-based key pair type with specified parameters.
   *
   * @param alg The algorithm name for KeyPairGenerator initialization
   * @param specName The name of the elliptic curve specification
   * @param modulusSize The expected size of DER-encoded public key in bytes
   */
  KeyPairType(String alg, String specName, int modulusSize) {
    this.alg = alg;
    this.specName = specName;
    this.modulusSize = modulusSize;
    spec = new ECGenParameterSpec(specName);
  }
}
