package prng.generator;

import java.security.MessageDigest;
import java.security.SecureRandomParameters;
import java.util.Arrays;

/**
 * An implementation of the NIST HMAC-based Deterministic Random Number Generator as defined in SP800-90A.
 *
 * @author Simon Greatrix
 */
public class NistHmacRandom extends BaseRandom {

  /** Empty byte array */
  private static final byte[] NO_BYTES = new byte[0];

  /** Serial version UID */
  private static final long serialVersionUID = 1L;



  /**
   * Implementation built around SHA-1
   *
   * @author Simon Greatrix
   */
  public static class RandomHmacSHA1 extends NistHmacRandom {

    /** serial version UID */
    private static final long serialVersionUID = 1L;


    /** New instance */
    public RandomHmacSHA1() {
      super(null, HashSpec.SPEC_SHA1, 0, null, null, null);
    }


    /** New instance */
    public RandomHmacSHA1(SecureRandomParameters parameters) {
      super(null, HashSpec.SPEC_SHA1, 0, null, null, getPersonalization(parameters));
      verifyStrength(parameters, 128);
    }

  }



  /**
   * Implementation built around SHA-256
   *
   * @author Simon Greatrix
   */
  public static class RandomHmacSHA256 extends NistHmacRandom {

    /** serial version UID */
    private static final long serialVersionUID = 1L;


    /** New instance */
    public RandomHmacSHA256() {
      super(null, HashSpec.SPEC_SHA256, 0, null, null, null);
    }


    /** New instance */
    public RandomHmacSHA256(SecureRandomParameters parameters) {
      super(null, HashSpec.SPEC_SHA256, 0, null, null, getPersonalization(parameters));
      verifyStrength(parameters, 256);
    }

  }



  /**
   * Implementation built around SHA-512
   *
   * @author Simon Greatrix
   */
  public static class RandomHmacSHA512 extends NistHmacRandom {

    /** serial version UID */
    private static final long serialVersionUID = 1L;


    /** New instance */
    public RandomHmacSHA512() {
      super(null, HashSpec.SPEC_SHA512, 0, null, null, null);
    }


    /** New instance */
    public RandomHmacSHA512(SecureRandomParameters parameters) {
      super(null, HashSpec.SPEC_SHA512, 0, null, null, getPersonalization(parameters));
      // SHA-512 is still only 256 bits according to NIST
      verifyStrength(parameters, 256);
    }

  }



  /**
   * The hash function
   */
  private final MessageDigest digest;

  /** Algorithm parameters */
  private final HashSpec spec;

  /**
   * The "Key" parameter as defined in the specification.
   */
  private byte[] key;

  /**
   * The "V" Value parameter as defined in the specification.
   */
  private byte[] value;


  /**
   * Create a new deterministic random number generator
   *
   * @param source          entropy source (null means use the default source)
   * @param spec            digest specification (required)
   * @param resistance      number of operations between reseeds. Zero reseeds on every operation, one reseeds on every alternate operation, and so on.
   * @param entropy         optional initial entropy
   * @param nonce           an optional nonce
   * @param personalization an optional personalization value
   */
  public NistHmacRandom(
      SeedSource source, HashSpec spec, int resistance,
      byte[] entropy, byte[] nonce, byte[] personalization
  ) {
    super(source,
        new InitialMaterial(source, entropy, nonce, personalization,
            spec.seedLength, spec.seedLength
        ),
        resistance, spec.seedLength, spec.outputLength
    );
    this.spec = spec;
    digest = spec.getInstance();

    key = new byte[spec.outputLength];
    value = new byte[spec.outputLength];
    Arrays.fill(value, (byte) 1);
  }


  @Override
  public String getAlgorithm() {
    return "Nist/Hmac" + spec.algorithm;
  }


  @Override
  protected int getStrength() {
    return spec.strength;
  }


  /**
   * Calculate a HMAC where the message consists of three parts
   *
   * @param myKey   HMAC key
   * @param myValue HMAC message part 1
   * @param extra   HMAC message part 2
   * @param message HMAC message part 3
   *
   * @return hmac value
   */
  private byte[] hmac(
      byte[] myKey, byte[] myValue, byte extra,
      byte[] message
  ) {
    byte[] ipad = myKey.clone();
    byte[] opad = myKey.clone();
    int len = myKey.length;
    for (int i = 0; i < len; i++) {
      ipad[i] ^= (byte) 0x36;
      opad[i] ^= (byte) 0x5c;
    }

    digest.update(ipad);
    digest.update(myValue);
    digest.update(extra);
    digest.update(message);
    byte[] hash = digest.digest();
    digest.update(opad);
    digest.update(hash);
    return digest.digest();
  }


  /**
   * Calculate a HMAC where the message is a single value
   *
   * @param myKey   HMAC key
   * @param myValue HMAC message
   *
   * @return hmac value
   */
  private byte[] hmac(byte[] myKey, byte[] myValue) {
    byte[] ipad = myKey.clone();
    byte[] opad = myKey.clone();
    int len = myKey.length;
    for (int i = 0; i < len; i++) {
      ipad[i] ^= (byte) 0x36;
      opad[i] ^= (byte) 0x5c;
    }

    digest.update(ipad);
    digest.update(myValue);
    byte[] hash = digest.digest();
    digest.update(opad);
    digest.update(hash);
    return digest.digest();
  }


  @Override
  protected void implNextBytes(int off, byte[] bytes) {
    int outLen = spec.outputLength;
    int len = bytes.length - off;
    int fullLoops = len / outLen;
    int lastSize = len - (fullLoops * outLen);

    for (int i = 0; i < fullLoops; i++) {
      value = hmac(key, value);
      System.arraycopy(value, 0, bytes, off, outLen);
      off += outLen;
    }

    // final block
    if (lastSize > 0) {
      value = hmac(key, value);
      System.arraycopy(value, 0, bytes, off, lastSize);
      setSpares(value, lastSize, outLen - lastSize);
    }

    update(NO_BYTES);
  }


  @Override
  protected void implSetSeed(byte[] seed) {
    if (seed == null) {
      seed = new byte[0];
    }
    update(seed);
  }


  @Override
  protected void initialise(byte[] material) {
    update(material);
  }


  /**
   * Update the key and value using the given entropy input
   *
   * @param entropy entropy (maybe empty)
   */
  private void update(byte[] entropy) {
    key = hmac(key, value, (byte) 0, entropy);
    value = hmac(key, value);

    if (entropy.length == 0) {
      return;
    }
    key = hmac(key, value, (byte) 1, entropy);
    value = hmac(key, value);
  }

}
