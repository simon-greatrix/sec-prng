package prng;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import prng.generator.BaseRandom;
import prng.generator.HashSpec;
import prng.generator.NistCipherRandom;
import prng.generator.NistHashRandom;
import prng.generator.NistHmacRandom;
import prng.generator.SeedSource;
import prng.seeds.PermutingSeedSource;
import prng.seeds.ZeroSeedSource;
import prng.utility.NonceFactory;

/**
 * A builder of secure random instances.
 *
 * @author Simon
 */
public abstract class SecureRandomBuilder {

  /**
   * Hash algorithms used by the RNGs.
   */
  public enum Hash {
    /** NIST SHA1 */
    SHA1(HashSpec.SPEC_SHA1),

    /** NIST SHA-256 */
    SHA256(HashSpec.SPEC_SHA256),

    /** NIST SHA-512 */
    SHA512(HashSpec.SPEC_SHA512);

    /** The specification associated with this hash. */
    final HashSpec spec;


    /**
     * New instance.
     *
     * @param spec the specification
     */
    Hash(HashSpec spec) {
      this.spec = spec;
    }
  }



  /**
   * Possible entropy sources
   *
   * @author Simon
   */
  public enum Source {
    /**
     * A seed source which implements the "Fortuna" algorithm to mix entropy from different sources and which draws upon all the entropy provided by SYSTEM and
     * additional entropy collectors.
     */
    FORTUNA() {
      @Override
      SeedSource getSource() {
        return Fortuna.SOURCE;
      }
    },

    /**
     * A dummy seed source used to achieve a totally deterministic output. Seed bytes work through every possible permutation of byte values. Note that for a
     * deterministic stream, the nonce, entropy and personalization inputs must be specified as well.
     */
    PERMUTE() {
      @Override
      SeedSource getSource() {
        return new PermutingSeedSource();
      }
    },

    /**
     * A seed source which draws from the built-in random number generators.
     */
    SYSTEM() {
      @Override
      SeedSource getSource() {
        return SystemRandom.SOURCE;
      }
    },

    /**
     * A dummy seed source used to achieve a totally deterministic output. All seed bytes are zero. Note that for a deterministic stream, the nonce, entropy and
     * personalization inputs must be specified as well.
     */
    ZERO() {
      @Override
      SeedSource getSource() {
        return ZeroSeedSource.SOURCE;
      }
    };


    /**
     * Get the source implementation.
     *
     * @return the source
     */
    abstract SeedSource getSource();
  }



  /**
   * Create Cipher based RNGs.
   *
   * @author Simon
   */
  static class CipherBuilder extends SecureRandomBuilder {

    @Override
    public Supplier<BaseRandom> asTemplate() {
      SeedSource mySource = source.getSource();
      byte[] myPersonalization = (personalization != null) ? personalization.clone() : NonceFactory.personalization();
      return () -> new NistCipherRandom(mySource, laziness, null, null, myPersonalization);
    }


    @Override
    protected BaseRandom createSpi() {
      return new NistCipherRandom(source.getSource(), laziness, entropy, nonce, personalization);
    }


    @Override
    String getClassName() {
      return NistCipherRandom.class.getName();
    }


    @Override
    public SecureRandomBuilder hash(Hash newHash) {
      if (hash != null) {
        throw new IllegalArgumentException("Hash is not applicable to cipher based algorithms");
      }
      return this;
    }


    @Override
    protected SecureRandomBuilder newCopy() {
      return new CipherBuilder();
    }

  }



  /**
   * Create Hash based RNGs.
   *
   * @author Simon
   */
  static class HashBuilder extends SecureRandomBuilder {

    @Override
    public Supplier<BaseRandom> asTemplate() {
      SeedSource mySource = source.getSource();
      HashSpec mySpec = hash.spec;
      byte[] myPersonalization = (personalization != null) ? personalization.clone() : NonceFactory.personalization();
      return () -> new NistHashRandom(mySource, mySpec, laziness, null, null, myPersonalization);
    }


    @Override
    protected BaseRandom createSpi() {
      return new NistHashRandom(source.getSource(), hash.spec, laziness, entropy, nonce, personalization);
    }


    @Override
    String getClassName() {
      return NistHashRandom.class.getName();
    }


    @Override
    protected SecureRandomBuilder newCopy() {
      return new HashBuilder();
    }

  }



  /**
   * Create HMAC based RNGs.
   *
   * @author Simon
   */
  static class HmacBuilder extends SecureRandomBuilder {

    @Override
    public Supplier<BaseRandom> asTemplate() {
      SeedSource mySource = source.getSource();
      HashSpec mySpec = hash.spec;
      byte[] myPersonalization = (personalization != null) ? personalization.clone() : NonceFactory.personalization();
      return () -> new NistHmacRandom(mySource, mySpec, laziness, null, null, myPersonalization);
    }


    @Override
    protected BaseRandom createSpi() {
      return new NistHmacRandom(source.getSource(), hash.spec, laziness, entropy, nonce, personalization);
    }


    @Override
    String getClassName() {
      return NistHmacRandom.class.getName();
    }


    @Override
    protected SecureRandomBuilder newCopy() {
      return new HmacBuilder();
    }

  }


  /**
   * Create a secure random based upon an AES cipher.
   *
   * @return a builder
   */
  public static SecureRandomBuilder cipher() {
    return new CipherBuilder();
  }


  /**
   * Create a secure random based upon a secure hash algorithm (SHA).
   *
   * @return a builder
   */
  public static SecureRandomBuilder hash() {
    return new HashBuilder();
  }


  /**
   * Create a secure random based upon a hash based message authentication code (HMAC)
   *
   * @return a builder
   */
  public static SecureRandomBuilder hmac() {
    return new HmacBuilder();
  }


  /** Entropy bytes for RNG. */
  protected byte[] entropy = null;

  /** Hash algorithm for RNG. */
  protected Hash hash = Hash.SHA256;

  /** Laziness (resistance) value for RNG. */
  protected int laziness = 0;

  /** Nonce for RNG. */
  protected byte[] nonce = null;

  /** Personalization for RNG. */
  protected byte[] personalization = null;

  /** Entropy source for RNG. */
  protected Source source = Source.FORTUNA;

  /** Multiplex generators for thread safety (true - default), or use synchronization (false)? */
  protected boolean threadSafe = true;


  /**
   * Create a template from which new instances can be generated. Note the template ignores the entropy and nonce values.
   *
   * @return a supplier of BaseRandom instances
   */
  public abstract Supplier<BaseRandom> asTemplate();


  /**
   * Build the secure random instance.
   *
   * @return the instance
   */
  public SecureRandom build() {
    return new SecureRandomImpl(buildSpi());
  }


  /**
   * Build the secure random SPI instance.
   *
   * @return the instance
   */
  BaseRandom buildSpi() {
    // Synchronize to prevent re-use of nonce and entropy
    BaseRandom base = createSpi();

    // Do not re-use nonce and entropy. Note these arrays are passed by reference to InitialMaterial and cleared when that is used.
    nonce = null;
    entropy = null;
    return base;
  }


  public SecureRandomBuilder copy() {
    return newCopy()
        .laziness(laziness)
        .personalization(getPersonalization())
        .source(source)
        .threadSafe(threadSafe);
  }


  protected abstract BaseRandom createSpi();


  /**
   * Specify the bytes for initial entropy provided to the RNG. If null, then the system will provide initial entropy from the Fortuna implementation. The
   * default is null.
   *
   * @param data the entropy to use (or null)
   *
   * @return this
   */
  public SecureRandomBuilder entropy(byte[] data) {
    entropy = data;
    return this;
  }


  /**
   * Get the build specification as an attribute map.
   *
   * @return an attribute map
   */
  public Map<String, String> getAttributes() {
    HashMap<String, String> map = new HashMap<>();
    map.put("Laziness", String.valueOf(laziness));
    if (personalization != null) {
      map.put("Personalization", Base64.getUrlEncoder().encodeToString(personalization));
    }
    map.put("Source", source.name());
    map.put("ThreadSafe", Boolean.toString(threadSafe));
    return Map.copyOf(map);
  }


  /**
   * Get the class name that implements the RNG.
   *
   * @return the class name
   */
  abstract String getClassName();


  public byte[] getEntropy() {
    return entropy != null ? entropy.clone() : null;
  }


  public Hash getHash() {
    return hash;
  }


  public int getLaziness() {
    return laziness;
  }


  public byte[] getNonce() {
    return nonce != null ? nonce.clone() : null;
  }


  public byte[] getPersonalization() {
    return personalization != null ? personalization.clone() : null;
  }


  public Source getSource() {
    return source;
  }


  /**
   * Specify the hash algorithm used by a hash or HMAC based RNG. Not applicable to other RNGs.
   *
   * @param newHash the hash to use
   *
   * @return this
   */
  public SecureRandomBuilder hash(Hash newHash) {
    if (newHash == null) {
      throw new IllegalArgumentException("Hash algorithm must be specified");
    }
    hash = newHash;
    return this;
  }


  public boolean isThreadSafe() {
    return threadSafe;
  }


  /**
   * How lazy is the generator when it comes to requesting new entropy? NIST refers to this parameter as "resistance" but lower values are more secure than
   * higher ones.
   *
   * @param lazy the amount of laziness (a non-negative integer). The default value is 0.
   *
   * @return this
   */
  public SecureRandomBuilder laziness(int lazy) {
    if (lazy < 0) {
      throw new IllegalArgumentException(
          "Laziness must be non-negative, not " + lazy);
    }
    laziness = lazy;
    return this;
  }


  protected abstract SecureRandomBuilder newCopy();


  /**
   * Specify the bytes for a nonce used to initialise the RNG. If null, then the system will provide a nonce. The default is null.
   *
   * @param data the nonce to use (or null)
   *
   * @return this
   */
  public SecureRandomBuilder nonce(byte[] data) {
    nonce = data;
    return this;
  }


  /**
   * Specify the bytes for personalization used to initialise the RNG. If null, then the system will provide some data uniquely tied to this JVM. The default is
   * null.
   *
   * @param data the personalization to use (or null)
   *
   * @return this
   */
  public SecureRandomBuilder personalization(byte[] data) {
    personalization = data;
    return this;
  }


  /**
   * Specify the seed data source. The default is FORTUNA.
   *
   * @param newSource the source
   *
   * @return this
   */
  public SecureRandomBuilder source(Source newSource) {
    if (newSource == null) {
      throw new IllegalArgumentException(
          "Source must be specified, not null");
    }
    source = newSource;
    return this;
  }


  /**
   * Should a "thread safe" SPI be used? All SecureRandom instances are thread safe regardless of the SPI. If the SPI is not "thread safe", then the
   * SecureRandom instance will use synchronization locks to ensure it is only invoked by a single thread at a time. The default is "true".
   *
   * @param isSafe If true, multiplex multiple SPIs. If false, synchronize on a single SPI.
   *
   * @return this
   */
  public SecureRandomBuilder threadSafe(boolean isSafe) {
    threadSafe = isSafe;
    return this;
  }

}
