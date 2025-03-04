package hyphanet.base;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Utility class providing system-related information and detection capabilities. This class handles
 * operating system detection, architecture identification, and system charset operations.
 *
 * <p>The class is marked as final to prevent inheritance as it's designed to be a utility class
 * with static members only.
 */
public final class SystemInfo {

  /**
   * The detected operating system for the current environment.
   *
   * <p>This field is initialized during class loading and remains constant throughout the
   * application lifecycle. The detection is performed using system properties and filesystem
   * characteristics.
   *
   * @see #detectOperatingSystem()
   */
  public static final OperatingSystem DETECTED_OS = detectOperatingSystem();

  /**
   * The detected CPU architecture for the current environment.
   *
   * <p><b>Note:</b> This detection may not always be 100% accurate in cases where:
   *
   * <ul>
   *   <li>32-bit vs 64-bit detection is ambiguous
   *   <li>Wrong JVM architecture is used for the platform
   *   <li>System uses architecture emulation or compatibility layers
   * </ul>
   */
  public static final CPUArchitecture DETECTED_ARCH = detectCPUArchitecture();

  /**
   * Enumeration of supported operating systems with platform-specific capabilities.
   *
   * <p>Each enum constant provides information about:
   *
   * <ul>
   *   <li>Windows compatibility
   *   <li>macOS compatibility
   *   <li>Unix/Linux compatibility
   * </ul>
   */
  public enum OperatingSystem {
    UNKNOWN(false, false, false),
    MACOS(false, true, true),
    LINUX(false, false, true),
    FREEBSD(false, false, true),
    GENERIC_UNIX(false, false, true),
    WINDOWS(true, false, false);

    OperatingSystem(boolean win, boolean mac, boolean unix) {
      this.isWindows = win;
      this.isMac = mac;
      this.isUnix = unix;
    }

    public boolean isWindows() {
      return isWindows;
    }

    public boolean isMac() {
      return isMac;
    }

    public boolean isUnix() {
      return isUnix;
    }

    private final boolean isWindows;
    private final boolean isMac;
    private final boolean isUnix;
  }

  /** CPU architecture using modern naming conventions */
  public enum CPUArchitecture {
    UNKNOWN,
    X86_32,
    X86_64,
    ARM_32,
    ARM_64,
    RISCV_64,
    PPC_32,
    PPC_64,
    IA64
  }

  private SystemInfo() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Returns the Charset corresponding to the system's "file.encoding" property.
   *
   * <p>This method provides access to the system's default file encoding, which is typically:
   *
   * <ul>
   *   <li>On Windows: Set to the user's configured system language
   *   <li>On Unix/Linux: Usually UTF-8
   *   <li>On macOS: Usually UTF-8
   * </ul>
   *
   * <p>The method first attempts to use the system's default charset. If this fails, it falls back
   * to UTF-8 to ensure a valid Charset is always returned.
   *
   * @return The system's default file encoding Charset, or UTF-8 if the default cannot be
   *     determined
   * @see Charset#defaultCharset()
   * @see StandardCharsets#UTF_8
   */
  public static Charset getFileEncodingCharset() {
    try {
      return Charset.forName(Charset.defaultCharset().displayName());
    } catch (Exception e) {
      return StandardCharsets.UTF_8;
    }
  }

  /**
   * Detects the current operating system by analyzing system properties and filesystem
   * characteristics.
   *
   * <p>The detection process follows these steps:
   *
   * <ol>
   *   <li>Checks the "os.name" system property for known operating system names
   *   <li>Uses case-insensitive substring matching for common OS identifiers
   *   <li>Falls back to filesystem separator analysis if the OS name is not recognized
   * </ol>
   *
   * <p>Supported operating systems:
   *
   * <ul>
   *   <li>Windows
   *   <li>macOS
   *   <li>Linux
   *   <li>FreeBSD
   *   <li>Generic Unix-like systems
   * </ul>
   *
   * @return The detected {@link OperatingSystem} enum value representing the current operating
   *     system. Returns {@code OperatingSystem.UNKNOWN} if the system cannot be identified.
   * @implNote This method uses pattern matching in switch expressions (requires Java 17+). The
   *     fallback detection uses the filesystem path separator character to distinguish between
   *     Unix-like systems ('/') and others.
   * @see System#getProperty(String)
   * @see OperatingSystem
   */
  private static OperatingSystem detectOperatingSystem() {
    return switch (System.getProperty("os.name").toLowerCase(Locale.ROOT)) {
      case String s when s.contains("win") -> OperatingSystem.WINDOWS;
      case String s when s.contains("mac") -> OperatingSystem.MACOS;
      case String s when s.contains("linux") -> OperatingSystem.LINUX;
      case String s when s.contains("freebsd") -> OperatingSystem.FREEBSD;
      case String s when s.contains("unix") -> OperatingSystem.GENERIC_UNIX;
      default ->
          Path.of("").getFileSystem().getSeparator().equals("/")
              ? OperatingSystem.GENERIC_UNIX
              : OperatingSystem.UNKNOWN;
    };
  }

  /**
   * Detects the CPU architecture of the current system by analyzing system properties.
   *
   * <p>Supports detection of the following CPU architectures:
   *
   * <table>
   *   <tr><th>Architecture</th><th>Identifiers</th></tr>
   *   <tr>
   *     <td>32-bit x86</td>
   *     <td>x86, i386, i486, i586, i686, etc.</td>
   *   </tr>
   *   <tr>
   *     <td>64-bit x86</td>
   *     <td>amd64, x86_64, x86-64, em64t</td>
   *   </tr>
   *   <tr>
   *     <td>ARM</td>
   *     <td>arm (32-bit), aarch64 (64-bit)</td>
   *   </tr>
   *   <tr>
   *     <td>PowerPC</td>
   *     <td>ppc, powerpc (32-bit), ppc64 (64-bit)</td>
   *   </tr>
   *   <tr>
   *     <td>Intel Itanium</td>
   *     <td>ia64</td>
   *   </tr>
   *   <tr>
   *     <td>RISC-V</td>
   *     <td>riscv64 (64-bit)</td>
   *   </tr>
   * </table>
   *
   * @return The detected {@link CPUArchitecture} enum value representing the current CPU
   *     architecture. Returns {@code CPUArchitecture.UNKNOWN} if the architecture cannot be
   *     identified.
   * @implNote This method uses pattern matching in switch expressions (requires Java 17+). The
   *     detection is based on the "os.arch" system property value. Regular expressions are used to
   *     match various architecture naming conventions.
   * @see System#getProperty(String)
   * @see CPUArchitecture
   */
  private static CPUArchitecture detectCPUArchitecture() {
    return switch (System.getProperty("os.arch").toLowerCase(Locale.ROOT)) {
      case String s when s.matches("x86|i[3-9]86") -> CPUArchitecture.X86_32;
      case String s when s.matches("amd64|x86[-_]?64|em64t") -> CPUArchitecture.X86_64;
      case "aarch64" -> CPUArchitecture.ARM_64;
      case String s when s.startsWith("arm") -> CPUArchitecture.ARM_32;
      case String s when s.matches("ppc|powerpc") -> CPUArchitecture.PPC_32;
      case "ppc64" -> CPUArchitecture.PPC_64;
      case String s when s.startsWith("ia64") -> CPUArchitecture.IA64;
      case String s when s.contains("riscv64") -> CPUArchitecture.RISCV_64;
      default -> CPUArchitecture.UNKNOWN;
    };
  }
}
