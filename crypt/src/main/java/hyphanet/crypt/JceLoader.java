package hyphanet.crypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;

/**
 * A loader for Java Cryptography Extension (JCE) providers that manages the initialization and
 * availability of different cryptographic providers in the system.
 * <p>
 * This class handles the loading of the following providers:
 * <ul>
 *   <li>BouncyCastle - Primary cryptography provider</li>
 *   <li>NSS (Network Security Services) - PKCS11 provider</li>
 *   <li>SUN - Basic security operations</li>
 *   <li>SunJCE - JCE operations</li>
 * </ul>
 * </p>
 */
public class JceLoader {
    /**
     * Exception thrown when there are problems loading or initializing cryptographic
     * providers.
     */
    public static class LoadException extends Exception {
        /**
         * Constructs a new LoadException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause   the cause of the exception
         */
        public LoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The BouncyCastle provider instance.
     */
    private static final Provider BouncyCastle;

    /**
     * The NSS provider instance. May be null if not available.
     */
    private static final Provider NSS; // optional, may be null

    /**
     * The SUN provider instance. May be null if not enabled.
     */
    private static final Provider SUN; // optional, may be null

    /**
     * The SunJCE provider instance. May be null if not enabled.
     */
    private static final Provider SunJCE; // optional, may be null

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(JceLoader.class);

    static {
        Provider p = null;

        // NSS is preferred over BC, add it first
        if (checkUse("use.NSS", "false")) {
            try {
                p = new NSSLoader().load(checkUse("prefer.NSS"));
                KeyGenerator kgen = KeyGenerator.getInstance("AES", "SunPKCS11-NSS");
                kgen.init(256);
            } catch (GeneralSecurityException e) {
                logger.warn("Error with SunPKCS11-NSS. Unlimited policy file not installed.",
                            e);
            } catch (Exception e) {
                // FIXME what about Windows/MacOSX/etc?
                logger.warn("Unable to load SunPKCS11-NSS crypto provider. Not fatal, but " +
                            "performance may be affected.", e);
            }
        }
        NSS = p;

        // BouncyCastle loading
        p = null;
        if (checkUse("use.BC.I.know.what.I.am.doing")) {
            try {
                p = new BouncyCastleLoader().load();
            } catch (LoadException e) {
                logger.error("SERIOUS PROBLEM: Unable to load or use BouncyCastle provider.",
                             e);
            }
        }
        BouncyCastle = p;

        // SunJCE loading
        if (checkUse("use.SunJCE")) {
            try {
                KeyGenerator kgen = KeyGenerator.getInstance("AES", "SunJCE");
                kgen.init(256);
            } catch (Exception e) {
                logger.warn("Error with SunJCE. Unlimited policy file not installed.", e);
            }
            SunJCE = Security.getProvider("SunJCE");
        } else {
            SunJCE = null;
        }

        // SUN provider loading
        SUN = checkUse("use.SUN") ? Security.getProvider("SUN") : null;
    }

    /**
     * Returns a clone of the BouncyCastle provider instance.
     *
     * @return a clone of the BouncyCastle provider, or null if not available
     */
    public static Provider getBouncyCastle() {
        return BouncyCastle;
    }

    /**
     * Returns a clone of the NSS provider instance.
     *
     * @return a clone of the NSS provider, or null if not available
     */
    public static Provider getNSS() {
        return NSS;
    }

    /**
     * Returns a clone of the SUN provider instance.
     *
     * @return a clone of the SUN provider, or null if not available
     */
    public static Provider getSUN() {
        return SUN;
    }

    /**
     * Returns a clone of the SunJCE provider instance.
     *
     * @return a clone of the SunJCE provider, or null if not available
     */
    public static Provider getSunJCE() {
        return SunJCE;
    }

    public static void main(String[] args) {
        dumpLoaded();
    }

    /**
     * Logs information about all loaded providers.
     */
    public static void dumpLoaded() {
        logger.info("BouncyCastle: {}", BouncyCastle);
        logger.info("SunPKCS11-NSS: {}", NSS);
        logger.info("SUN: {}", SUN);
        logger.info("SunJCE: {}", SunJCE);
    }

    /**
     * Checks if a specific JCE feature should be used.
     *
     * @param prop the property name to check
     *
     * @return true if the feature should be used, false otherwise
     */
    private static boolean checkUse(String prop) {
        return checkUse(prop, "true");
    }

    /**
     * Checks if a specific JCE feature should be used with a default value.
     *
     * @param prop the property name to check
     * @param def  the default value if the property is not set
     *
     * @return true if the feature should be used, false otherwise
     */
    private static boolean checkUse(String prop, String def) {
        return "true".equalsIgnoreCase(System.getProperty("freenet.jce." + prop, def));
    }

    /**
     * Loads and configures the BouncyCastle cryptographic provider.
     */
    private static final class BouncyCastleLoader {
        /**
         * Loads and verifies the BouncyCastle provider.
         *
         * @return the loaded BouncyCastle provider
         *
         * @throws LoadException if the provider cannot be loaded or required algorithms are
         *                       unavailable
         */
        private Provider load() throws LoadException {
            Provider p = Security.getProvider("BC");
            try {
                if (p == null) {
                    var c =
                        Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
                    p = (Provider) c.getConstructor().newInstance();
                    Security.addProvider(p);
                    logger.debug("Loaded BouncyCastle provider: {}", p);
                } else {
                    logger.debug("Found BouncyCastle provider: {}", p);
                }
            } catch (Exception e) {
                throw new LoadException("Cannot load BouncyCastle provider", e);
            }
            try {
                // Verify required algorithms are available
                KeyAgreement.getInstance("ECDH", p);
                Signature.getInstance("SHA256withECDSA", p);
            } catch (Exception e) {
                throw new LoadException(
                    "Cannot use required algorithm from BouncyCaste provider", e);
            }
            return p;
        }
    }

    /**
     * Loads and configures the NSS (Network Security Services) cryptographic provider.
     */
    private static final class NSSLoader {
        /**
         * Loads and configures the NSS provider.
         *
         * @param atfirst if true, inserts the provider at the first position in the provider
         *                list
         *
         * @return the loaded NSS provider
         *
         * @throws LoadException if the provider cannot be loaded or configured
         */
        private Provider load(boolean atfirst) throws LoadException {
            Provider nssProvider = null;
            for (Provider p : Security.getProviders()) {
                if (p.getName().matches("^SunPKCS11-(?i)NSS.*$")) {
                    nssProvider = p;
                    break;
                }
            }
            if (nssProvider == null) {
                try {
                    File nssFile = File.createTempFile("nss", ".cfg");
                    nssFile.deleteOnExit();

                    try (OutputStream os = new FileOutputStream(nssFile);
                         OutputStreamWriter osw = new OutputStreamWriter(os,
                                                                         StandardCharsets.ISO_8859_1);
                         BufferedWriter bw = new BufferedWriter(osw)) {
                        bw.write("name=NSScrypto\n");
                        bw.write("nssDbMode=noDb\n");
                        bw.write("attributes=compatibility\n");
                    }

                    nssProvider = Security.getProvider("SunPKCS11");
                    nssProvider = nssProvider.configure(nssFile.getPath());

                    if (atfirst) {
                        Security.insertProviderAt(nssProvider, 1);
                    } else {
                        Security.addProvider(nssProvider);
                    }
                    logger.debug("Loaded NSS provider {}", nssProvider);
                } catch (Exception e) {
                    throw new LoadException("Failed to load NSS provider", e);
                }
            } else {
                logger.debug("Found NSS provider {}", nssProvider);
            }
            return nssProvider;
        }
    }
}
