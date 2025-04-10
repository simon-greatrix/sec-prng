package prng.utility;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implementation of NIST SP800-108 key derivation function in double pipeline mode.
 *
 * <p>Example usage:
 *
 * <p><code><pre>
 *   // privateKey and publicKey are compatible elliptic curve keys
 *   // kdfContext is some data known to both parties
 *   //
 *   // Calculate the key material using elliptic curve Diffie-Hellman
 *   KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
 *   keyAgreement.init(privateKey);
 *   keyAgreement.doPhase(publicKey,true);
 *   byte[] secret = keyAgreement.generateSecret();
 *
 *   // Derive the IV and key from the secret and the shared context
 *   byte[] iv = KDF.derive(secret,"IV",kdfContext,12);
 *   byte[] rawKey = KDF.derive(secret,"KEY",kdfContext,32);
 *   SecretKey secretKey = new SecretKeySpec(rawKey,"AES");
 *
 *   // Create a cipher in the normal way
 *   gcmSpec = new GCMParameterSpec(128, iv);
 *   cipher = Cipher.getInstance("AES/GCM/NoPadding");
 *   cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
 * </pre></code>
 *
 * @author Simon Greatrix on 30/10/2017.
 */
public class KDF extends SecureRandomSpi {

  public static final String ALG_SHA3_256 = "HmacSHA3-256";

  public static final String ALG_SHA3_512 = "HmacSHA3-512";

  public static final String ALG_SHA_256 = "HmacSHA256";

  public static final String ALG_SHA_512 = "HmacSHA512";

  public static final String ALG_SHA_512_256 = "HmacSHA512/256";

  private static final long serialVersionUID = 1L;



  private static class SRI extends SecureRandom {

    private static final long serialVersionUID = 1;


    SRI(KDF kdf) {
      super(kdf, null);
    }

  }


  /**
   * Derive key bytes from the provided input.
   *
   * @param keyMaterial the key material (Converted as UTF-16BE)
   * @param label       the label for the resulting key bytes
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   *
   * @return the key bytes
   */
  public static byte[] derive(String keyMaterial, String label, byte[] context, int length) {
    return derive(keyMaterial, label, context, length, ALG_SHA_256);
  }


  /**
   * Derive key bytes from the provided input.
   *
   * @param keyMaterial the key material (Converted as UTF-16BE)
   * @param label       the label for the resulting key bytes
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   * @param algorithm   the desired keyed MAC algorithm
   *
   * @return the key bytes
   */
  public static byte[] derive(String keyMaterial, String label, byte[] context, int length, String algorithm) {
    return derive(keyMaterial.getBytes(StandardCharsets.UTF_16BE), label.getBytes(StandardCharsets.UTF_16BE), context, length, algorithm);
  }


  /**
   * Derive key bytes from the provided input.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes (Converted as UTF-16BE)
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   *
   * @return the key bytes
   */
  public static byte[] derive(byte[] keyMaterial, String label, byte[] context, int length, String algorithm) {
    return derive(keyMaterial, label.getBytes(StandardCharsets.UTF_16BE), context, length, algorithm);
  }


  /**
   * Derive key bytes from the provided input.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   *
   * @return the key bytes
   */
  public static byte[] derive(byte[] keyMaterial, byte[] label, byte[] context, int length, String algorithm) {
    KDF kdf = new KDF(keyMaterial, label, context, length, algorithm);
    byte[] output = new byte[length];
    kdf.engineNextBytes(output);
    return output;
  }


  /**
   * Derive key bytes from the provided input.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes (Converted as UTF-16BE)
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   *
   * @return the key bytes
   */
  public static byte[] derive(byte[] keyMaterial, String label, byte[] context, int length) {
    return derive(keyMaterial, label.getBytes(StandardCharsets.UTF_16BE), context, length, ALG_SHA_256);
  }


  /**
   * Derive key bytes from the provided input.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   *
   * @return the key bytes
   */
  public static byte[] derive(byte[] keyMaterial, byte[] label, byte[] context, int length) {
    return derive(keyMaterial, label, context, length, ALG_SHA_256);
  }


  private static byte[] standardizeKey(byte[] keyMaterial, String prf) {
    String digest;
    if (prf.equals(ALG_SHA_256)) {
      digest = "SHA-256";
    } else if (prf.equals(ALG_SHA3_512)) {
      digest = "SHA3-512";
    } else if (keyMaterial.length > 32) {
      digest = "SHA-512";
    } else {
      digest = "SHA-256";
    }

    try {
      return MessageDigest.getInstance(digest).digest(keyMaterial);
    } catch (NoSuchAlgorithmException e) {
      throw new InternalError("Expected SHA algorithm " + digest + " is not supported", e);
    }
  }


  /**
   * Input into second pipe-line.
   */
  private final byte[] input;

  /**
   * The HMAC signing key.
   */
  private final SecretKeySpec secretKey;

  /**
   * Last block generated.
   */
  private byte[] block;

  /**
   * Block count.
   */
  private int counter = 0;

  /**
   * The HMac instance used to generate bytes.
   */
  private transient Mac mac;

  /**
   * Position in current block.
   */
  private int position = 32;

  /**
   * Previous output in first pipe-line.
   */
  private byte[] previous;


  /**
   * Derive key bytes from the provided input. The length is neither a restriction nor a requirement, but an expectation. Different values for the length
   * produce different bits. Reading less or more is not a problem.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes (Converted using UTF-16BE)
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   */
  public KDF(byte[] keyMaterial, String label, byte[] context, int length) {
    this(keyMaterial, label.getBytes(StandardCharsets.UTF_16BE), context, length, ALG_SHA_256);
  }


  /**
   * Derive key bytes from the provided input. The length is neither a restriction nor a requirement, but an expectation. Different values for the length
   * produce different bits. Reading less or more is not a problem.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes (Converted using UTF-16BE)
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   */
  public KDF(byte[] keyMaterial, String label, byte[] context, int length, String algorithm) {
    this(keyMaterial, label.getBytes(StandardCharsets.UTF_16BE), context, length, algorithm);
  }


  /**
   * Derive key bytes from the provided input. The length is neither a restriction nor a requirement, but an expectation. Different values for the length
   * produce different bits. Reading less or more is not a problem.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   */
  public KDF(byte[] keyMaterial, byte[] label, byte[] context, int length) {
    this(keyMaterial, label, context, length, ALG_SHA_256);
  }


  /**
   * Derive key bytes from the provided input. The length is neither a restriction nor a requirement, but an expectation. Different values for the length
   * produce different bits. Reading less or more is not a problem.
   *
   * @param keyMaterial the key material
   * @param label       the label for the resulting key bytes
   * @param context     the context of this key derivation
   * @param length      the number of bytes required
   */
  public KDF(byte[] keyMaterial, byte[] label, byte[] context, int length, String algorithm) {
    try {
      // Standardise the key material.
      secretKey = new SecretKeySpec(standardizeKey(keyMaterial, algorithm), algorithm);
      mac = Mac.getInstance(algorithm);
      mac.init(secretKey);
    } catch (GeneralSecurityException e) {
      throw new AssertionError("Fatal exception: " + algorithm + " is required", e);
    }

    // Create the IV by concatenating the label, 0x00, the context, and the
    // required bit length
    final byte[] iv = new byte[label.length + 1 + context.length + 4];
    System.arraycopy(label, 0, iv, 0, label.length);
    iv[label.length] = 0;
    System.arraycopy(context, 0, iv, label.length + 1, context.length);
    int l = length * 8;
    for (int i = 1; i <= 4; i++) {
      iv[iv.length - i] = (byte) l;
      l >>>= 8;
    }

    // First pipe-line's input is the IV
    previous = iv;

    // The second pipe-line's input is Hmac || counter || IV, so add 36 bytes
    input = new byte[iv.length + 36];
    System.arraycopy(iv, 0, input, 36, iv.length);
  }


  @Override
  protected byte[] engineGenerateSeed(int numBytes) {
    throw new UnsupportedOperationException();
  }


  @Override
  protected void engineNextBytes(byte[] bytes) {
    int l = bytes.length;

    // first use up any spare bytes
    int spare = 32 - position;
    int use = Math.min(spare, l);
    if (use > 0) {
      System.arraycopy(block, position, bytes, 0, use);
      position += use;
      l -= use;
    }
    if (l == 0) {
      return;
    }
    int p = use;

    // step through full blocks
    while (l >= 32) {
      nextBlock();
      System.arraycopy(block, 0, bytes, p, 32);
      p += 32;
      l -= 32;
    }

    // partial block if needed
    if (l > 0) {
      nextBlock();
      System.arraycopy(block, 0, bytes, p, l);
      position = l;
    } else {
      position = 32;
    }
  }


  @Override
  protected void engineSetSeed(byte[] seed) {
    throw new UnsupportedOperationException();
  }


  /**
   * Get a secure random instance that provides bytes as this KDF would do. This allows the use of a KeyFactory, KeyGenerator, or KeyPairGenerator with the
   * KDF.
   *
   * @return a SecureRandom instance
   */
  public SecureRandom getSecureRandom() {
    return new SRI(this);
  }


  private void nextBlock() {
    // Update first pipe-line
    previous = mac.doFinal(previous);

    // Create input to second pipe-line
    System.arraycopy(previous, 0, input, 0, previous.length);
    counter++;
    int j = counter;
    for (int k = 35; k >= 32; k--) {
      input[k] = (byte) (0xff & j);
      j >>>= 8;
    }

    // Update second pipe-line and copy to output
    block = mac.doFinal(input);
    position = 0;
  }


  private void readObject(ObjectInputStream in) throws IOException {
    try {
      mac = Mac.getInstance(ALG_SHA_256);
      mac.init(secretKey);
    } catch (GeneralSecurityException e) {
      throw new IOException("Invalid or unusable KDF", e);
    }
  }

}
