package prng;

import java.security.Provider;
import java.security.Security;

import prng.nist.NistCipherRandom;
import prng.nist.NistHashRandom;
import prng.nist.NistHmacRandom;

/**
 * Security service provider
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
        prov.putService(new Service(prov,"SecureRandom","Nist-SHA1",NistHashRandom.RandomSHA1.class.getName(),null,null));
        prov.putService(new Service(prov,"SecureRandom","Nist-SHA256",NistHashRandom.RandomSHA256.class.getName(),null,null));
        prov.putService(new Service(prov,"SecureRandom","Nist-SHA512",NistHashRandom.RandomSHA512.class.getName(),null,null));
        prov.putService(new Service(prov,"SecureRandom","Nist-HmacSHA1",NistHmacRandom.RandomHmacSHA1.class.getName(),null,null));
        prov.putService(new Service(prov,"SecureRandom","Nist-HmacSHA256",NistHmacRandom.RandomHmacSHA256.class.getName(),null,null));
        prov.putService(new Service(prov,"SecureRandom","Nist-HmacSHA512",NistHmacRandom.RandomHmacSHA512.class.getName(),null,null));
        prov.putService(new Service(prov,"SecureRandom","Nist-AES256",NistCipherRandom.class.getName(),null,null));
        Provider[] provs = Security.getProviders();
        Security.insertProviderAt(prov, provs.length);
        PROVIDER = prov;
    }
    
    /**
     * Create instance
     */
    protected SecureRandomProvider() {
        super("SecureRandomProvider",1.1, "Provides Secure PRNGs");
    }
}