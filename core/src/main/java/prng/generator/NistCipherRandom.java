package prng.generator;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandomParameters;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * An implementation of the NIST Cipher-based Deterministic Random Number Generator as defined in SP800-90A. Only the AES-256 cipher is available as an
 * underlying cipher.
 *
 * @author Simon Greatrix
 */
public class NistCipherRandom extends BaseRandom {

  /** Byte array of 48 zeros */
  private static final byte[] EMPTY_BYTES = new byte[48];

  /** A SHA-384 used to ensure that an input seed is converted to 384 bits. */
  private static final MessageDigest KEY_DF;

  /** Serial version UID */
  private static final long serialVersionUID = 1L;


  /**
   * Ensure a seed value has 384 bits.
   *
   * @param seed a proposed seed
   *
   * @return a seed with 384 bits
   */
  private static byte[] trimSeed(byte[] seed) {
    if (seed.length == 48) {
      return seed;
    }
    synchronized (KEY_DF) {
      KEY_DF.reset();
      KEY_DF.update(seed);
      return KEY_DF.digest();
    }
  }


  static {
    try {
      KEY_DF = MessageDigest.getInstance("SHA-384");
    } catch (NoSuchAlgorithmException e) {
      throw new InternalError("SHA-384 not available");
    }
  }

  /**
   * Single block output buffer
   */
  private final byte[] buffer = new byte[16];

  /**
   * The cipher function
   */
  private final Cipher cipher;

  /**
   * The "Key" parameter as defined in the specification.
   */
  private final byte[] key;

  /**
   * The "V" Value parameter as defined in the specification.
   */
  private final byte[] value;


  /**
   * Create a new deterministic random number generator with generated initialisation parameters.
   */
  public NistCipherRandom() {
    this(null, 0, null, null, null);
  }


  /**
   * Create a new deterministic random number generator with generated initialisation parameters.
   */
  public NistCipherRandom(SecureRandomParameters parameters) {
    this(null, 0, null, null, getPersonalization(parameters));
    verifyStrength(parameters, 256);
  }


  /**
   * Create a new deterministic random number generator
   *
   * @param source          entropy source (null means use the default source)
   * @param resistance      number of operations between reseeds. Zero reseeds on every operation, one reseeds on every alternate operation, and so on.
   * @param entropy         optional initial entropy
   * @param nonce           an optional nonce
   * @param personalization an optional personalization value
   */
  public NistCipherRandom(SeedSource source, int resistance, byte[] entropy, byte[] nonce, byte[] personalization) {
    super(source, new InitialMaterial(source, entropy, nonce, personalization, 32, 48), resistance, 48, 16);

    try {
      cipher = Cipher.getInstance("AES/ECB/NoPadding");
    } catch (NoSuchAlgorithmException e) {
      throw new InternalError("AES not supported");
    } catch (NoSuchPaddingException e) {
      throw new InternalError("NoPadding not supported");
    }

    key = new byte[32];
    value = new byte[16];
  }


  @Override
  public String getAlgorithm() {
    return "Nist/AES256";
  }


  @Override
  protected int getStrength() {
    return 256;
  }


  @Override
  protected void implNextBytes(int off, byte[] bytes) {
    int len = bytes.length - off;
    int fullLoops = len / 16;
    int lastSize = len - (fullLoops * 16);

    try {
      for (int i = 0; i < fullLoops; i++) {
        incr();
        cipher.update(value, 0, 16, bytes, off + i * 16);
        off += 16;
      }

      // final block
      if (lastSize > 0) {
        incr();
        cipher.update(value, 0, 16, buffer, 0);
        System.arraycopy(buffer, 0, bytes, off, lastSize);
        setSpares(buffer, lastSize, 16 - lastSize);
      }
    } catch (GeneralSecurityException e) {
      throw new InternalError("Cryptographic failure", e);
    }

    implSetSeed(EMPTY_BYTES);
  }


  /**
   * Update this PRNG with new seed material
   *
   * @param seedMaterial the new seed material
   */
  @Override
  protected void implSetSeed(byte[] seedMaterial) {
    seedMaterial = trimSeed(seedMaterial);
    byte[] temp = new byte[48];
    try {
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
      for (int i = 0; i < 3; i++) {
        incr();
        cipher.update(value, 0, 16, temp, i * 16);
      }
    } catch (GeneralSecurityException e) {
      throw new InternalError("Cryptographic failure", e);
    }
    for (int i = 0; i < 48; i++) {
      temp[i] ^= seedMaterial[i];
    }

    System.arraycopy(temp, 0, key, 0, 32);
    System.arraycopy(temp, 32, value, 0, 16);
  }


  /**
   * Increment the value
   */
  private void incr() {
    for (int j = 0; j < 16; j++) {
      byte b = (byte) (value[j] + 1);
      value[j] = b;
      if (b != 0) {
        break;
      }
    }
  }


  @Override
  protected void initialise(byte[] material) {
    implSetSeed(material);
  }

}
