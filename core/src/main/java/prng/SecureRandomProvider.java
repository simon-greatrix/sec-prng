package prng;

import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.SecureRandomBuilder.Hash;
import prng.SecureRandomBuilder.Source;
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
        prov.putService(new Service(prov, "SecureRandom", "Nist/SHA256",
                NistHashRandom.RandomSHA256.class.getName(),
                Arrays.asList("SHA256"), null));
        prov.putService(new Service(prov, "SecureRandom", "Nist/HmacSHA256",
                NistHmacRandom.RandomHmacSHA256.class.getName(),
                Arrays.asList("Nist", "HmacSHA256"), null));

        prov.putService(new Service(prov, "SecureRandom", "Nist/SHA512",
                NistHashRandom.RandomSHA512.class.getName(),
                Arrays.asList("SHA512"), null));
        prov.putService(new Service(prov, "SecureRandom", "Nist/HmacSHA512",
                NistHmacRandom.RandomHmacSHA512.class.getName(),
                Arrays.asList("HmacSHA512"), null));

        prov.putService(new Service(prov, "SecureRandom", "Nist/AES256",
                NistCipherRandom.class.getName(),
                Arrays.asList("AES256", "Nist/AES", "AES"), null));

        prov.putService(new Service(prov, "SecureRandom", "Nist/SHA1",
                NistHashRandom.RandomSHA1.class.getName(),
                Arrays.asList("SHA1"), null));
        prov.putService(new Service(prov, "SecureRandom", "Nist/HmacSHA1",
                NistHmacRandom.RandomHmacSHA1.class.getName(),
                Arrays.asList("HmacSHA1"), null));

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
     * Pre-main argument which allows this provider to be activated via a
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

    /**
     * Pattern to recognise the supported algorithm names
     */
    private static final Pattern ALG_PATTERN = Pattern.compile(
            "^(?:nist/)?(aes(?:256)?|(?:hmac)?sha(?:1|256|512))\\?(.*)$",
            Pattern.CASE_INSENSITIVE);


    @Override
    public synchronized Service getService(String type, String algorithm) {
        if( type == null || !type.equals("SecureRandom") ) {
            return null;
        }

        // if no parameters, use the standard types
        if( algorithm.indexOf('?') == -1 ) {
            LOG.debug("No parameters in {} so using standard type", algorithm);
            return super.getService(type, algorithm);
        }

        // Check against the regular expression
        Matcher matcher = ALG_PATTERN.matcher(algorithm);
        if( !matcher.matches() ) {
            LOG.debug("Algorithm {} is not matched.", algorithm);
            return null;
        }

        // Try to match the algorithm name
        SecureRandomBuilder builder;
        String impl = matcher.group(1).toLowerCase();
        if( impl.startsWith("aes") ) {
            LOG.debug("Algorithm {} is AES", algorithm);
            builder = SecureRandomBuilder.cipher();
        } else {
            // HMAC or HASH
            if( impl.startsWith("hmac") ) {
                builder = SecureRandomBuilder.hmac();
                LOG.debug("Algorithm {} is HMAC", algorithm);
            } else {
                builder = SecureRandomBuilder.hash();
                LOG.debug("Algorithm {} is HASH", algorithm);
            }

            // Now what SHA does it use?
            if( impl.endsWith("1") ) {
                builder = builder.hash(Hash.SHA1);
                LOG.debug("Algorithm {} is using SHA-1", algorithm);
            } else if( impl.endsWith("256") ) {
                builder = builder.hash(Hash.SHA256);
                LOG.debug("Algorithm {} is SHA-256", algorithm);
            } else {
                builder = builder.hash(Hash.SHA512);
                LOG.debug("Algorithm {} is SHA-512", algorithm);
            }
        }

        // Parse the parameters
        String[] params = matcher.group(2).split("&");
        for(String p:params) {
            int e = p.indexOf('=');
            if( e == -1 ) {
                LOG.debug("Parameter {} does not contain an '='", p);
                return null;
            }
            String k = p.substring(0, e).toLowerCase();
            String v = p.substring(e + 1);

            switch (k.charAt(0)) {
            case 'e':
                // entropy
                if( !"entropy".startsWith(k) ) {
                    LOG.debug("Unknown parameter key {}", k);
                    return null;
                }
                try {
                    builder = builder.entropy(Base64.getUrlDecoder().decode(v));
                } catch (IllegalArgumentException exc) {
                    LOG.debug("Value '{}' passed for 'entropy' was invalid", v,
                            exc);
                    return null;
                }
                break;
            case 'l':
                // laziness or lazy
                if( !("laziness".startsWith(k) || "lazy".startsWith(k)) ) {
                    LOG.debug("Unknown parameter key {}", k);
                    return null;
                }
                try {
                    builder = builder.laziness(Integer.decode(v).intValue());
                } catch (IllegalArgumentException exc) {
                    LOG.debug("Value '{}' passed for 'laziness' was invalid", v,
                            exc);
                    return null;
                }
                break;
            case 'n':
                // nonce
                if( !"nonce".startsWith(k) ) {
                    LOG.debug("Unknown parameter key {}", k);
                    return null;
                }
                try {
                    builder = builder.nonce(Base64.getUrlDecoder().decode(v));
                } catch (IllegalArgumentException exc) {
                    LOG.debug("Value '{}' passed for 'nonce' was invalid", v,
                            exc);
                    return null;
                }
                break;
            case 'p':
                // personalization
                if( !"personalization".startsWith(k) ) {
                    LOG.debug("Unknown parameter key {}", k);
                    return null;
                }
                try {
                    builder = builder.personalization(
                            Base64.getUrlDecoder().decode(v));
                } catch (IllegalArgumentException exc) {
                    LOG.debug(
                            "Value '{}' passed for 'personalization' was invalid",
                            v, exc);
                    return null;
                }
                break;
            case 's':
                // seed source
                if( !"source".startsWith(k) ) {
                    LOG.debug("Unknown parameter key {}", k);
                    return null;
                }
                
                // find match source
                Source src = null;
                for(Source source:Source.values()) {
                    if( source.name().equalsIgnoreCase(v) ) {
                        src = source;
                        break;
                    }
                }
                if( src != null ) {
                    builder = builder.source(src);
                } else {
                    LOG.debug("Value '{}' passed for 'source' was invalid", v);
                    return null;
                }
                break;
            default:
                // unrecognised
                LOG.debug("Unknown parameter key {}", k);
                return null;

            }
        }

        return new CustomService(this, algorithm, builder);
    }

    /**
     * A service which implements a specific set of initialisation parameters
     * for the RNG.
     * 
     * @author Simon
     *
     */
    static class CustomService extends Service {
        /** The builder which build instances. */
        private final SecureRandomBuilder builder;


        /**
         * New instance
         * 
         * @param provider
         *            the SecureRandom provider
         * @param algorithm
         *            the algorithm implemented
         * @param builder
         *            the builder for new instances
         */
        public CustomService(Provider provider, String algorithm,
                SecureRandomBuilder builder) {
            super(provider, "SecureRandom", algorithm, builder.getClassName(),
                    null, null);
            this.builder = builder;
        }


        @Override
        public Object newInstance(Object constructorParameter)
                throws NoSuchAlgorithmException {
            return builder.buildSpi();
        }
    }
}