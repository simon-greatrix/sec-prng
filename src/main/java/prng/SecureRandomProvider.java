package prng;

import java.security.Provider;
import java.security.Security;

import prng.nist.NistCipherRandom;
import prng.nist.NistHashRandom;
import prng.nist.NistHmacRandom;
import prng.util.Config;

/**
 * Security service provider
 * 
 * @author Simon Greatrix
 *
 */
public class SecureRandomProvider extends Provider {
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
        if( replace != null ) {
            Service s = prov.getService("SecureRandom", replace);
            if( s != null ) {
                Service s2 = new Service(prov, "SecureRandom", "SHA1PRNG",
                        s.getClassName(), null, null);
                prov.putService(s2);
            } else {
                Config.LOG.warn(
                        "Cannot replace SHA1PRNG with unknown algorithm {}",
                        replace);
            }
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
        if( isPrimary ) {
            Security.insertProviderAt(SecureRandomProvider.PROVIDER, 1);
        } else {
            Provider[] provs = Security.getProviders();
            Security.insertProviderAt(SecureRandomProvider.PROVIDER,
                    provs.length);
        }
    }


    /**
     * Create instance
     */
    protected SecureRandomProvider() {
        super("SecureRandomProvider", 1.0, "Provides Secure PRNGs");
    }
}