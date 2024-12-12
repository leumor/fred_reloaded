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

public class JceLoader {
    public static class LoadException extends Exception {
        public LoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Provider BouncyCastle;
    private static final Provider NSS; // optional, may be null
    private static final Provider SUN; // optional, may be null
    private static final Provider SunJCE; // optional, may be null
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

    public static Provider getBouncyCastle() {
        return BouncyCastle != null ? (Provider) BouncyCastle.clone() : null;
    }

    public static Provider getNSS() {
        return NSS != null ? (Provider) NSS.clone() : null;
    }

    public static Provider getSUN() {
        return SUN != null ? (Provider) SUN.clone() : null;
    }

    public static Provider getSunJCE() {
        return SunJCE != null ? (Provider) SunJCE.clone() : null;
    }

    public static void main(String[] args) {
        dumpLoaded();
    }

    public static void dumpLoaded() {
        logger.info("BouncyCastle: {}", BouncyCastle);
        logger.info("SunPKCS11-NSS: {}", NSS);
        logger.info("SUN: {}", SUN);
        logger.info("SunJCE: {}", SunJCE);
    }

    private static boolean checkUse(String prop) {
        return checkUse(prop, "true");
    }

    private static boolean checkUse(String prop, String def) {
        return "true".equalsIgnoreCase(System.getProperty("freenet.jce." + prop, def));
    }

    private static final class BouncyCastleLoader {
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
                // We don't want totally unusable provider
                KeyAgreement.getInstance("ECDH", p);
                Signature.getInstance("SHA256withECDSA", p);
            } catch (Exception e) {
                throw new LoadException(
                    "Cannot use required algorithm from BouncyCaste provider", e);
            }
            return p;
        }
    }

    private static final class NSSLoader {
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
