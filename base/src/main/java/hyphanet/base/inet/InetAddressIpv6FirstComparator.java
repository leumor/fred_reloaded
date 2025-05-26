package hyphanet.base.inet;

import hyphanet.base.Fields;
import hyphanet.base.lru.LruCache;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Comparator;

/**
 * Comparator for IP addresses that sorts IPv6 addresses before IPv4 addresses to enable selecting
 * the first. This comparator orders {@link InetAddress} objects based on several criteria to
 * prioritize certain types of addresses:
 *
 * <ul>
 *   <li>Null addresses are considered last.
 *   <li>Non-any-local (broadcast) addresses are preferred.
 *   <li>Non-loop-back addresses are preferred.
 *   <li>Non-link-local addresses are preferred.
 *   <li>Reachable addresses (determined via a ping) are preferred over unreachable ones.
 *   <li>LAN (site-local) addresses are preferred over global addresses.
 *   <li>IPv6 addresses (16 bytes) are preferred over IPv4 addresses (4 bytes).
 *   <li>If all above criteria are equal, addresses are compared based on their hash codes and then
 *       their byte representations.
 * </ul>
 *
 * <p>This comparator is designed to be used in scenarios where prioritizing IPv6 addresses or
 * specific types of addresses is necessary. It uses a cache to store reachability results to
 * optimize performance by avoiding repeated network checks.
 *
 * <p>The comparator is implemented as a singleton with a static instance available via {@link
 * #COMPARATOR}.
 *
 * @author toad
 */
@SuppressWarnings("java:S6548")
public class InetAddressIpv6FirstComparator implements Comparator<InetAddress> {
  /**
   * Default maximum time in milliseconds to wait for a ping response when checking address
   * reachability.
   */
  public static final long DEFAULT_MAX_PING_TIME = 1500L;

  /** Singleton instance of the comparator. */
  public static final InetAddressIpv6FirstComparator COMPARATOR =
      new InetAddressIpv6FirstComparator();

  /**
   * A Least Recently Used (LRU) cache for storing the reachability status of {@link InetAddress}
   * objects. This cache helps to avoid repeated and potentially time-consuming calls to {@link
   * InetAddress#isReachable(int)}.
   *
   * <p>The cache has the following properties:
   *
   * <ul>
   *   <li><b>Capacity:</b> It can store up to 1000 entries. When the cache exceeds this capacity,
   *       the least recently used entry is evicted.
   *   <li><b>Expiration Policy:</b> Entries are automatically removed if they have been in the
   *       cache for more than 300,000 milliseconds (5 minutes).
   *   <li><b>Key:</b> The hash code of the {@link InetAddress} (obtained via {@link
   *       InetAddress#hashCode()}) is used as the key.
   *   <li><b>Value:</b> A {@link Boolean} indicating whether the address was found to be reachable
   *       ({@code true}) or not ({@code false}).
   * </ul>
   *
   * The cache is utilized by the {@link #resolveReachability(InetAddress, int)} method.
   *
   * @see LruCache
   * @see #resolveReachability(InetAddress, int)
   * @see InetAddress#isReachable(int)
   */
  private static final LruCache<Integer, Boolean> REACHABILITY_CACHE = new LruCache<>(1000, 300000);

  /**
   * Compares two {@link InetAddress} objects based on the criteria defined in the class
   * documentation.
   *
   * <p>The comparison follows these steps in order:
   *
   * <ol>
   *   <li>Null checks: Non-null addresses are preferred.
   *   <li>Non-any-local addresses are preferred.
   *   <li>Non-loopback addresses are preferred.
   *   <li>Non-link-local addresses are preferred.
   *   <li>Reachable addresses are preferred over unreachable ones.
   *   <li>LAN (site-local) addresses are preferred over global addresses.
   *   <li>IPv6 addresses are preferred over IPv4 addresses based on address length.
   *   <li>If all above are equal, compare hash codes.
   *   <li>If hash codes are equal, compare the byte arrays of the addresses.
   * </ol>
   *
   * <p>This method uses a cache to store reachability results to optimize performance.
   *
   * @param addr1 the first address to compare
   * @param addr2 the second address to compare
   * @return a negative integer, zero, or a positive integer as the first address is less than,
   *     equal to, or greater than the second
   */
  @Override
  public int compare(InetAddress addr1, InetAddress addr2) {
    if (addr1 == null && addr2 == null) {
      return 0;
    }
    if (addr1 == null) {
      return 1;
    }
    if (addr2 == null) {
      return -1;
    }
    if (addr1.equals(addr2)) {
      return 0;
    }

    // Prefer non-any-local (broadcast) addresses.
    var cmp = Boolean.compare(addr1.isAnyLocalAddress(), addr2.isAnyLocalAddress());
    if (cmp != 0) {
      return cmp;
    }

    // Prefer non-loopback addresses.
    cmp = Boolean.compare(addr1.isLoopbackAddress(), addr2.isLoopbackAddress());
    if (cmp != 0) {
      return cmp;
    }

    // Prefer non-link-local addresses.
    cmp = Boolean.compare(addr1.isLinkLocalAddress(), addr2.isLinkLocalAddress());
    if (cmp != 0) {
      return cmp;
    }

    // Prefer reachable addresses to unreachable ones.
    int hash1 = addr1.hashCode();
    int hash2 = addr2.hashCode();
    boolean reachable1 = resolveReachability(addr1, hash1);
    boolean reachable2 = resolveReachability(addr2, hash2);
    cmp = Boolean.compare(reachable2, reachable1); // reverse order to prefer true
    if (cmp != 0) {
      return cmp;
    }

    // Prefer LAN (site-local) addresses over global addresses.
    cmp = Boolean.compare(addr2.isSiteLocalAddress(), addr1.isSiteLocalAddress());
    if (cmp != 0) {
      return cmp;
    }

    // Prefer IPv6 over IPv4 based on address length (IPv6 addresses have 16 bytes, IPv4 have 4).
    byte[] bytes1 = addr1.getAddress();
    byte[] bytes2 = addr2.getAddress();
    cmp = Integer.compare(bytes2.length, bytes1.length);
    if (cmp != 0) {
      return cmp;
    }

    // Fallback: compare by hash code.
    cmp = Integer.compare(hash1, hash2);
    if (cmp != 0) {
      return cmp;
    }

    // Final fallback: compare the byte arrays.
    return Fields.compareBytes(bytes1, bytes2);
  }

  /**
   * Determines if the given address is reachable, using a cache to store results.
   *
   * <p>This method checks the cache first. If the reachability is not cached, it performs a
   * reachability check using {@link InetAddress#isReachable(int)} with a timeout of {@link
   * #DEFAULT_MAX_PING_TIME} milliseconds. The result is then stored in the cache for future use.
   *
   * @param address the address to check
   * @param hash the hash code of the address, used as the cache key
   * @return {@code true} if the address is reachable, {@code false} otherwise
   */
  private boolean resolveReachability(InetAddress address, int hash) {
    var cached = REACHABILITY_CACHE.get(hash);
    if (cached != null) {
      return cached;
    }
    boolean reachable;
    try {
      reachable = address.isReachable((int) DEFAULT_MAX_PING_TIME);
    } catch (IOException _) {
      reachable = false;
    }
    REACHABILITY_CACHE.put(hash, reachable);
    return reachable;
  }
}
