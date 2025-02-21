package hyphanet.base.inet;

import hyphanet.base.Fields;
import java.net.InetAddress;
import java.util.Comparator;

/**
 * A fast, non-lexical comparator for {@link InetAddress} objects, designed for use in a {@link
 * java.util.TreeMap} to mitigate hash table exhaustion attacks by maliciously crafted IP addresses.
 * This comparator does not preserve the lexical order of IP addresses but instead uses an ordering
 * that is efficient for both IPv4 and IPv6 addresses.
 *
 * <p>The comparison is performed as follows:
 *
 * <ol>
 *   <li>If the two {@link InetAddress} objects are the same instance, they are considered equal.
 *   <li>Otherwise, their hash codes are compared. If the hash codes differ, the comparison result
 *       is based on the hash codes.
 *   <li>If the hash codes are equal, the byte arrays of the IP addresses are compared:
 *       <ul>
 *         <li>If the lengths of the byte arrays differ, IPv6 addresses (16 bytes) are considered
 *             less than IPv4 addresses (4 bytes), effectively preferring IPv6 over IPv4 in the
 *             ordering.
 *         <li>If the lengths are the same, the byte arrays are compared lexicographically using
 *             {@link Fields#compareBytes(byte[], byte[])}.
 *       </ul>
 * </ol>
 *
 * This approach ensures quick comparisons, especially for IPv4 addresses, by leveraging hash codes,
 * and provides a consistent ordering for both IPv4 and IPv6 addresses.
 *
 * <p>This comparator is consistent with the {@link InetAddress#equals(Object)} method, as both rely
 * on the byte arrays of the IP addresses, ignoring any cached hostnames.
 *
 * <p>This comparator is implemented as a singleton, with a single static instance available via
 * {@link #COMPARATOR}.
 *
 * @author toad
 */
@SuppressWarnings("java:S6548")
public class InetAddressComparator implements Comparator<InetAddress> {

  /** The singleton instance of this comparator. */
  public static final InetAddressComparator COMPARATOR = new InetAddressComparator();

  /**
   * Compares two {@link InetAddress} objects based on the logic described in the class
   * documentation.
   *
   * <p>This method first checks if the objects are identical. If not, it compares their hash codes
   * for an efficient initial ordering, which is particularly fast for IPv4 addresses due to their
   * shorter length and lower likelihood of hash collisions. If hash codes are equal, it compares
   * the byte arrays: IPv6 addresses (16 bytes) are ordered before IPv4 addresses (4 bytes) by
   * treating longer arrays as less than shorter ones; if lengths match, a lexicographical
   * comparison is performed.
   *
   * @param a the first {@link InetAddress} to compare
   * @param b the second {@link InetAddress} to compare
   * @return a negative integer, zero, or a positive integer as the first argument is less than,
   *     equal to, or greater than the second
   */
  @Override
  public int compare(InetAddress a, InetAddress b) {
    if (a == b) {
      return 0;
    }
    int hashA = a.hashCode();
    int hashB = b.hashCode();
    // By hash code first. Works really fast for IPv4.
    int cmp = Integer.compare(hashA, hashB);
    if (cmp != 0) {
      return cmp;
    }

    byte[] bytesA = a.getAddress();
    byte[] bytesB = b.getAddress();
    // Compare lengths: prefer IPv6 (longer array) over IPv4 (shorter array)
    // Fields.compareBytes doesn't go first by length, so check it here.
    cmp = Integer.compare(bytesB.length, bytesA.length);
    if (cmp != 0) {
      return cmp;
    }

    return Fields.compareBytes(bytesA, bytesB);
    // Hostnames in InetAddress are merely cached, equals() only operates on the byte[].
  }
}
