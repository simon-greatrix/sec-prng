package prng;

import java.security.AccessController;
import java.security.DrbgParameters;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import prng.SecureRandomBuilder.Hash;
import prng.SecureRandomBuilder.Source;
import prng.config.Config;
import prng.generator.HashSpec;

/**
 * Security service provider
 *
 * @author Simon Greatrix
 */
public class SecureRandomProvider extends Provider {

  /** Logger for instantiating this provider */
  public static final Logger LOG = LoggersFactory.getLogger(
      SecureRandomProvider.class);

  /** The name of this provider */
  public static final String NAME = "SecureRandomProvider";

  /** The provider instance */
  static final Provider PROVIDER;

  /**
   * Pattern to recognise the supported algorithm names
   */
  private static final Pattern ALG_PATTERN = Pattern.compile(
      "^(?:nist/)?(aes(?:256)?|(?:hmac)?sha-?(?:1|256|512))/?(.*)$",
      Pattern.CASE_INSENSITIVE
  );

  private static final Map<String, String> ATTR_THREAD_SAFE = Map.of("ThreadSafe", "true");

  /** serial version UID */
  private static final long serialVersionUID = 2L;



  /**
   * A service which implements a specific set of initialisation parameters for the RNG.
   *
   * @author Simon
   */
  static class CustomService extends Service {

    /** The builder which build instances. */
    private final SecureRandomBuilder builder;


    /**
     * New instance
     *
     * @param provider  the SecureRandom provider
     * @param algorithm the algorithm implemented
     * @param builder   the builder for new instances
     */
    public CustomService(Provider provider, String algorithm, List<String> aliases, SecureRandomBuilder builder) {
      super(provider, "SecureRandom", algorithm, builder.getClassName(),
          aliases, builder.getAttributes()
      );
      this.builder = builder;
    }


    @Override
    public Object newInstance(Object constructorParameter) {
      if (constructorParameter == null) {
        return builder.buildSpi();
      }
      if (!(constructorParameter instanceof DrbgParameters.Instantiation)) {
        throw new IllegalArgumentException(
            "Cannot use " + constructorParameter.getClass() + " as an initialisation parameter. Must be " + DrbgParameters.Instantiation.class);
      }

      DrbgParameters.Instantiation p = (DrbgParameters.Instantiation) constructorParameter;
      if (builder.getHash() != null && builder.getHash().spec == HashSpec.SPEC_SHA1) {
        if (p.getStrength() > 128) {
          throw new IllegalArgumentException("SHA-1 based DRBGs can only support 128 bits of strength, not " + p.getStrength());
        }
      } else {
        if (p.getStrength() > 256) {
          throw new IllegalArgumentException("Selected DRBG algorithm can only support 256 bits of strength, not " + p.getStrength());
        }
      }

      byte[] newPersonalization = p.getPersonalizationString();
      if (newPersonalization != null) {
        return builder.copy().personalization(newPersonalization).buildSpi();
      }
      return builder.buildSpi();
    }

  }


  /**
   * Install the Secure Random Provider.
   *
   * @param isPrimary if true, the Secure Random Provider will be the primary source for secure random number generators.
   */
  public static void install(boolean isPrimary) {
    final int position;
    if (isPrimary) {
      LOG.info("Installing provider as primary secure random provider");
      position = 1;
    } else {
      Provider[] providers = Security.getProviders();
      position = providers.length;
      LOG.info("Installing provider as preference {}", position);
    }

    // Inserting a provider is a privileged action
    try {
      AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
        Security.insertProviderAt(PROVIDER, position);
        return null;
      });
    } catch (SecurityException se) {
      LOG.error(
          "Cannot install security provider as lacking privilege \"insertProvider\" or \"insertProvider.SecureRandomProvider\"",
          se
      );
    }
  }


  /**
   * Pre-main argument which allows this provider to be activated via a command-line option
   *
   * @param args command line argument for this agent
   */
  public static void premain(String args) {
    LOG.info("Installing provider via agent");
    boolean isPrimary = args == null || !args.matches("\\s*sprimary\\s*=\\s*false\\s*");
    install(isPrimary);
  }


  static {
    // Add services to provider. The first added service is the default.

    SecureRandomProvider prov = new SecureRandomProvider();
    prov.putService(new CustomService(prov, "Nist/SHA-256",
        List.of("Nist/SHA256", "SHA-256", "SHA256"),
        SecureRandomBuilder.hash().hash(Hash.SHA256)
    ));
    prov.putService(new CustomService(prov, "Nist/HmacSHA-256",
        List.of("Nist", "Nist/HmacSHA256", "HmacSHA-256", "HmacSHA256"),
        SecureRandomBuilder.hmac().hash(Hash.SHA256)
    ));

    prov.putService(new CustomService(prov, "Nist/SHA-512",
        List.of("Nist/SHA512", "SHA-512", "SHA512"),
        SecureRandomBuilder.hash().hash(Hash.SHA512)
    ));
    prov.putService(new CustomService(prov, "Nist/HmacSHA-512",
        List.of("Nist/HmacSHA512", "HmacSHA-512", "HmacSHA512"),
        SecureRandomBuilder.hmac().hash(Hash.SHA512)
    ));

    prov.putService(new CustomService(prov, "Nist/AES-256",
        List.of("Nist/AES256", "AES-256", "AES256", "Nist/AES", "AES"),
        SecureRandomBuilder.cipher()
    ));

    prov.putService(new CustomService(prov, "Nist/SHA-1",
        List.of("Nist/SHA1", "SHA-1", "SHA1"),
        SecureRandomBuilder.hash().hash(Hash.SHA1)
    ));
    prov.putService(new CustomService(prov, "Nist/HmacSHA-1",
        List.of("Nist/HmacSHA1", "HmacSHA-1", "HmacSHA1"),
        SecureRandomBuilder.hmac().hash(Hash.SHA1)
    ));

    // Allow for the SHA1PRNG algorithm to be over-ridden with another
    Config config = Config.getConfig("", SecureRandomProvider.class);
    String replace = config.get("replaceSHA1PRNG");
    LOG.info("Replacing SHA1PRNG with {}", replace);
    if (replace != null) {
      Service s = prov.getService("SecureRandom", replace);
      if (s != null) {
        Service s2 = new Service(prov, "SecureRandom", "SHA1PRNG", s.getClassName(), null, ATTR_THREAD_SAFE);
        prov.putService(s2);
      } else {
        LOG.error("Cannot replace SHA1PRNG with unknown algorithm {}", replace);
      }
    }

    // Set the strong algorithm (a privileged action)
    String strongAlg = config.get("strongAlgorithm", "Nist/HmacSHA512") + ":" + NAME;
    LOG.info("Installing {} as a strong algorithm", strongAlg);
    try {
      AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
        String algs = Security.getProperty("securerandom.strongAlgorithms");
        if (algs == null || algs.trim().length() == 0) {
          algs = strongAlg;
        } else {
          algs = strongAlg + "," + algs;
        }
        Security.setProperty("securerandom.strongAlgorithms", algs);

        return null;
      });
    } catch (SecurityException se) {
      LOG.error(
          "Cannot install {} as a strong algorithm as lacking privilege \"getProperty.securerandom.strongAlgorithms\" or \"setProperty.securerandom.strongAlgorithms\"",
          strongAlg,
          se
      );
    }

    PROVIDER = prov;
  }


  /**
   * Create instance
   */
  protected SecureRandomProvider() {
    super(NAME, "1.1", "Provides Secure PRNGs using NIST algorithms");
  }


  /**
   * Special handling for "ThreadSafe" attributes on dynamically generated services.
   *
   * @param key the key
   *
   * @return the value, or null
   */
  @Override
  public String getProperty(String key) {
    if (!(key.startsWith("SecureRandom.") && key.endsWith(" ThreadSafe"))) {
      // normal query
      return super.getProperty(key);
    }

    // We may know the answer
    String v = super.getProperty(key);
    if (v != null) {
      return v;
    }

    String algorithm = key.substring(13, key.length() - 11);
    Service service = getService("SecureRandom", algorithm);
    if (service == null) {
      return null;
    }
    return service.getAttribute("ThreadSafe");
  }


  @Override
  public synchronized Service getService(String type, String algorithm) {
    if (!"SecureRandom".equals(type)) {
      return null;
    }

    // if no parameters, use the standard types
    if (algorithm.indexOf('?') == -1) {
      LOG.debug("No parameters in {} so using standard type", algorithm);
      return super.getService(type, algorithm);
    }

    // Check against the regular expression
    Matcher matcher = ALG_PATTERN.matcher(algorithm);
    if (!matcher.matches()) {
      LOG.debug("Algorithm {} is not matched.", algorithm);
      return null;
    }

    // Could be stored as a service
    Service service = super.getService(type, algorithm);
    if (service != null) {
      return service;
    }

    // We can store if it uses neither nonce nor entropy.
    boolean canStore = true;

    // Try to match the algorithm name
    SecureRandomBuilder builder;
    String impl = matcher.group(1).toLowerCase();
    if (impl.startsWith("aes")) {
      LOG.debug("Algorithm {} is AES", algorithm);
      builder = SecureRandomBuilder.cipher();
    } else {
      // HMAC or HASH
      if (impl.startsWith("hmac")) {
        builder = SecureRandomBuilder.hmac();
        LOG.debug("Algorithm {} is HMAC", algorithm);
      } else {
        builder = SecureRandomBuilder.hash();
        LOG.debug("Algorithm {} is HASH", algorithm);
      }

      // Now what SHA does it use?
      if (impl.endsWith("1")) {
        builder = builder.hash(Hash.SHA1);
        LOG.debug("Algorithm {} is using SHA-1", algorithm);
      } else if (impl.endsWith("256")) {
        builder = builder.hash(Hash.SHA256);
        LOG.debug("Algorithm {} is SHA-256", algorithm);
      } else {
        builder = builder.hash(Hash.SHA512);
        LOG.debug("Algorithm {} is SHA-512", algorithm);
      }
    }

    // Parse the parameters
    String[] params = matcher.group(2).split("&");
    for (String p : params) {
      int e = p.indexOf('=');
      if (e == -1) {
        LOG.debug("Parameter {} does not contain an '='", p);
        return null;
      }
      String k = p.substring(0, e).toLowerCase();
      String v = p.substring(e + 1);

      switch (k.charAt(0)) {
        case 'e':
          // entropy
          if (!"entropy".startsWith(k)) {
            LOG.debug("Unknown parameter key {}", k);
            return null;
          }
          try {
            builder = builder.entropy(Base64.getUrlDecoder().decode(v));
          } catch (IllegalArgumentException exc) {
            LOG.debug("Value '{}' passed for 'entropy' was invalid", v,
                exc
            );
            return null;
          }
          canStore = false;
          break;
        case 'l':
          // laziness or lazy
          if (!("laziness".startsWith(k) || "lazy".startsWith(k))) {
            LOG.debug("Unknown parameter key {}", k);
            return null;
          }
          try {
            builder = builder.laziness(Integer.decode(v));
          } catch (IllegalArgumentException exc) {
            LOG.debug("Value '{}' passed for 'laziness' was invalid", v,
                exc
            );
            return null;
          }
          break;
        case 'n':
          // nonce
          if (!"nonce".startsWith(k)) {
            LOG.debug("Unknown parameter key {}", k);
            return null;
          }
          try {
            builder = builder.nonce(Base64.getUrlDecoder().decode(v));
          } catch (IllegalArgumentException exc) {
            LOG.debug("Value '{}' passed for 'nonce' was invalid", v,
                exc
            );
            return null;
          }
          canStore = false;
          break;
        case 'p':
          // personalization
          if (!"personalization".startsWith(k)) {
            LOG.debug("Unknown parameter key {}", k);
            return null;
          }
          try {
            builder = builder.personalization(
                Base64.getUrlDecoder().decode(v));
          } catch (IllegalArgumentException exc) {
            LOG.debug(
                "Value '{}' passed for 'personalization' was invalid",
                v, exc
            );
            return null;
          }
          break;
        case 's':
          // seed source
          if (!"source".startsWith(k)) {
            LOG.debug("Unknown parameter key {}", k);
            return null;
          }

          // find match source
          Source src = null;
          for (Source source : Source.values()) {
            if (source.name().equalsIgnoreCase(v)) {
              src = source;
              break;
            }
          }
          if (src != null) {
            builder = builder.source(src);
          } else {
            LOG.debug("Value '{}' passed for 'source' was invalid", v);
            return null;
          }
          break;
        case 't':
          if (!"threadSafe".startsWith(k)) {
            LOG.debug("Unknown parameter key {}", k);
            return null;
          }
          builder.threadSafe(Boolean.parseBoolean(v));
          break;
        default:
          // unrecognised
          LOG.debug("Unknown parameter key {}", k);
          return null;

      }
    }

    service = new CustomService(this, algorithm, null, builder);
    if (canStore) {
      putService(service);
    }
    return service;
  }

}
