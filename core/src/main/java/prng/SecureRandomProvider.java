package prng;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.config.Config;
import prng.generator.NistCipherRandom;
import prng.generator.NistHashRandom;
import prng.generator.NistHmacRandom;

/**
 * Security service provider
 * 
 * @author Simon Greatrix
 *
 */
public class SecureRandomProvider extends Provider {
    /** The name of this provider */
    public static final String NAME = "SecureRandomProvider";

    /** Logger for instantiating this provider */
    public static final Logger LOG = LoggerFactory.getLogger(
            SecureRandomProvider.class);

    /** The provider instance */
    static final Provider PROVIDER;

    /** serial version UID */
    private static final long serialVersionUID = 2l;

    static {
        // add services to provider
        SecureRandomProvider prov = new SecureRandomProvider();
        prov.putService(new Service(prov, "SecureRandom", "Nist-SHA256",
                NistHashRandom.RandomSHA256.class.getName(), null, null));
        prov.putService(new Service(prov, "SecureRandom", "Nist-HmacSHA256",
                NistHmacRandom.RandomHmacSHA256.class.getName(), null, null));
        prov.putService(new Service(prov, "SecureRandom", "Nist-SHA512",
                NistHashRandom.RandomSHA512.class.getName(), null, null));
        prov.putService(new Service(prov, "SecureRandom", "Nist-HmacSHA512",
                NistHmacRandom.RandomHmacSHA512.class.getName(), null, null));
        prov.putService(new Service(prov, "SecureRandom", "Nist-AES256",
                NistCipherRandom.class.getName(), null, null));

        prov.putService(new Service(prov, "SecureRandom", "Nist-SHA1",
                NistHashRandom.RandomSHA1.class.getName(), null, null));
        prov.putService(new Service(prov, "SecureRandom", "Nist-HmacSHA1",
                NistHmacRandom.RandomHmacSHA1.class.getName(), null, null));

        // Allow for the SHA1PRNG algorithm to be over-ridden with another
        Config config = Config.getConfig("", SecureRandomProvider.class);
        String replace = config.get("replaceSHA1PRNG");
        LOG.info("Replacing SHA1PRNG with {}", replace);
        if( replace != null ) {
            Service s = prov.getService("SecureRandom", replace);
            if( s != null ) {
                Service s2 = new Service(prov, "SecureRandom", "SHA1PRNG",
                        s.getClassName(), null, null);
                prov.putService(s2);
            } else {
                LOG.error("Cannot replace SHA1PRNG with unknown algorithm {}",
                        replace);
            }
        }

        // Set the strong algorithm (a privileged action)
        String strongAlg = config.get("strongAlgorithm", "Nist-HmacSHA512")
                + ":" + NAME;
        LOG.info("Installing {} as a strong algorithm", strongAlg);
        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    String algs = Security.getProperty(
                            "securerandom.strongAlgorithms");
                    if( algs == null || algs.trim().length() == 0 ) {
                        algs = strongAlg;
                    } else {
                        algs = strongAlg + "," + algs;
                    }
                    Security.setProperty("securerandom.strongAlgorithms", algs);

                    return null;
                }
            });
        } catch (SecurityException se) {
            LOG.error(
                    "Cannot install {} as a strong algorithm as lacking privilege \"getProperty.securerandom.strongAlgorithms\" or \"setProperty.securerandom.strongAlgorithms\"",
                    strongAlg, se);
        }

        PROVIDER = prov;
    }


    /**
     * Install the Secure Random Provider.
     * 
     * @param isPrimary
     *            if true, the Secure Random Provider will be the primary source
     *            for secure random number generators.
     */
    public static void install(boolean isPrimary) {
        final int position;
        if( isPrimary ) {
            LOG.info("Installing provider as primary secure random provider");
            position = 1;
        } else {
            Provider[] provs = Security.getProviders();
            position = provs.length;
            LOG.info("Installing provider as preference {}",
                    Integer.valueOf(position));
        }

        // Inserting a provider is a privileged action
        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    Security.insertProviderAt(SecureRandomProvider.PROVIDER,
                            position);
                    return null;
                }
            });
        } catch (SecurityException se) {
            LOG.error(
                    "Cannot install security provider as lacking privilege \"insertProvider\" or \"insertProvider.SecureRandomProvider\"",
                    se);
        }
    }


    /**
     * Premain argument which allows this provider to be activated via a
     * command-line option
     * 
     * @param args
     *            command line argument for this agent
     */
    public static void premain(String args) {
        LOG.info("Installing provider via agent");
        boolean isPrimary = true;
        if( args != null ) {
            if( args.matches("\\s*sprimary\\s*=\\s*false\\s*") ) {
                isPrimary = false;
            }
        }
        install(isPrimary);
    }


    /**
     * Create instance
     */
    protected SecureRandomProvider() {
        super(NAME, 1.0, "Provides Secure PRNGs");
    }
}