package prng.utility;

import java.security.GeneralSecurityException;
import java.security.Provider;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implementation of AES key wrapping according to RFC-5649. Whilst Java does support AESWrap out-of-the-box, it is only compliant with the older RFC-3394 which
 * restricted key sizes to multiples of 8 bytes. As not all keys conform to this restriction, an implementation of the newer protocol is useful.
 *
 * @author Simon Greatrix on 26/10/2017.
 */
public class AESWrap {

  /**
   * Get the length embedded in an alternate initialisation vector.
   *
   * @param aiv the AIV
   *
   * @return the length
   */
  private static int getLength(byte[] aiv) {
    // The alternate initialisation vector must start with these 4 bytes
    if (aiv[0] != (byte) 0xA6 || aiv[1] != (byte) 0x59
        || aiv[2] != (byte) 0x59 || aiv[3] != (byte) 0xA6) {
      throw new IllegalArgumentException("Bad AIV");
    }

    // remaining 4 bytes are a 32-bit byte length.
    int length = 0;
    for (int i = 0; i < 4; i++) {
      length <<= 8;
      length |= 0xff & aiv[4 + i];
    }
    return length;
  }


  /**
   * AES cipher in decrypt mode for unwrapping.
   */
  private final Cipher unwrapper;

  /**
   * AES cipher in encrypt mode for wrapping.
   */
  private final Cipher wrapper;


  /**
   * Create an AES key wrap cipher from raw key material.
   *
   * @param raw the key material
   */
  public AESWrap(byte[] raw) {
    this(new SecretKeySpec(raw, "AES"));
  }


  /**
   * Create an AES key wrap cipher from a secret key
   *
   * @param key the AES secret key
   */
  public AESWrap(SecretKey key) {
    try {
      wrapper = Cipher.getInstance("AES/ECB/NoPadding");
      unwrapper = Cipher.getInstance("AES/ECB/NoPadding");
      wrapper.init(Cipher.ENCRYPT_MODE, key);
      unwrapper.init(Cipher.DECRYPT_MODE, key);
    } catch (GeneralSecurityException gse) {
      throw new AssertionError("AES not supported.", gse);
    }
  }


  /**
   * Create an AES key wrap cipher from a secret key using a specified provider.
   *
   * @param key      the AES secret key
   * @param provider the specific security provider to use
   */
  public AESWrap(SecretKey key, Provider provider) {
    try {
      wrapper = Cipher.getInstance("AES/ECB/NoPadding", provider);
      unwrapper = Cipher.getInstance("AES/ECB/NoPadding", provider);
      wrapper.init(Cipher.ENCRYPT_MODE, key);
      unwrapper.init(Cipher.DECRYPT_MODE, key);
    } catch (GeneralSecurityException gse) {
      throw new AssertionError("AES not supported.", gse);
    }
  }


  /**
   * Unwrap data.
   *
   * @param cipherText the wrapped data
   *
   * @return the unwrapped data
   *
   * @throws GeneralSecurityException if the decryption fails
   * @throws IllegalArgumentException if the input does not conform to the expectations for key wrapped data
   */
  public synchronized byte[] unwrap(byte[] cipherText)
      throws GeneralSecurityException {
    // Must be a multiple of 8 bytes.
    if ((cipherText.length & 0x7) != 0) {
      throw new IllegalArgumentException("Bad total length");
    }
    // Single block is a special case
    if (cipherText.length == 16) {
      // Just one round of AES
      byte[] plain = unwrapper.doFinal(cipherText);

      // get the length and check the AIV
      int len = getLength(plain);
      if ((len > 8) || (len < 1)) {
        throw new IllegalArgumentException("Bad length");
      }

      // Check the padding
      for (int i = len; i < 8; i++) {
        if (plain[i + 8] != 0) {
          throw new IllegalArgumentException("Bad padding");
        }
      }

      // All OK. Return result
      byte[] output = new byte[len];
      System.arraycopy(plain, 8, output, 0, len);
      return output;
    }

    // From the RFC:
    //
    // 1) Initialize variables.
    //
    // Set A = C[0]
    // For i = 1 to n
    // R[i] = C[i]
    //
    // 2) Compute intermediate values.
    //
    // For j = 5 to 0
    // For i = n to 1
    // B = AES-1(K, (A ^ t) | R[i]) where t = n*j+i
    // A = MSB(64, B)
    // R[i] = LSB(64, B)
    //
    // 3) Output results.
    //
    // If A is an appropriate initial value (see 2.2.3),
    // Then
    // For i = 1 to n
    // P[i] = R[i]
    // Else
    // Return an error

    // 'A' is first 64 bits of cipher text
    byte[] A = new byte[8];
    System.arraycopy(cipherText, 0, A, 0, 8);

    // 'R' is the rest of the cipher text, 64 bits at a time. Note that this
    // 'R' is 0 based, unlike the RFC.
    int n = (cipherText.length / 8) - 1;
    byte[][] R = new byte[n][8];
    for (int i = 0; i < n; i++) {
      System.arraycopy(cipherText, 8 + i * 8, R[i], 0, 8);
    }

    // Do the computation
    for (int j = 5; j >= 0; j--) {
      for (int i = n - 1; i >= 0; i--) {
        // NB: Add one as our R is zero based
        int t = n * j + i + 1;
        for (int k = 0; (k < 4) && (t != 0); k++) {
          A[7 - k] ^= t & 0xff;
          t >>>= 8;
        }
        unwrapper.update(A);
        byte[] B = unwrapper.doFinal(R[i]);
        System.arraycopy(B, 0, A, 0, 8);
        System.arraycopy(B, 8, R[i], 0, 8);
      }
    }

    // Check length
    int len = getLength(A);
    if ((len > 8 * n) || (len < 8 * (n - 1))) {
      // Length is more than 8 bytes off the mark
      throw new IllegalArgumentException("Bad length");
    }

    // Check the padding
    if (len < n * 8) {
      for (int i = len & 7; i < 8; i++) {
        if (R[n - 1][i] != 0) {
          // Expected a padding zero, but got something else
          throw new IllegalArgumentException("Bad padding");
        }
      }
    }

    // All OK. Return result
    byte[] output = new byte[len];
    for (int i = 0; i < n - 1; i++) {
      System.arraycopy(R[i], 0, output, i * 8, 8);
    }
    System.arraycopy(R[n - 1], 0, output, (n - 1) * 8, len - ((n - 1) * 8));
    return output;
  }


  /**
   * Wrap plain data.
   *
   * @param plainText the plain data
   *
   * @return the wrapped data
   *
   * @throws GeneralSecurityException if encryption fails
   */
  public synchronized byte[] wrap(byte[] plainText)
      throws GeneralSecurityException {
    // From the RFC
    //
    // MSB(j, W) Return the most significant j bits of W
    // LSB(j, W) Return the least significant j bits of W
    // ENC(K, B) AES Encrypt the 128-bit block B using key K
    // DEC(K, B) AES Decrypt the 128-bit block B using key K
    // V1 | V2 Concatenate V1 and V2
    // K The key-encryption key
    // m The number of octets in the key data
    // n The number of 64-bit blocks in the padded key data
    // Q[i] The ith plaintext octet in the key data
    // P[i] The ith 64-bit plaintext block in the padded key data
    // C[i] The ith 64-bit ciphertext data block
    // A The 64-bit integrity check register

    int m = plainText.length;

    // derive the AIV (Alternate Initialisation Vector)
    byte[] aiv = {
        (byte) 0xA6, (byte) 0x59, (byte) 0x59,
        (byte) 0xA6, 0, 0, 0, 0
    };
    for (int i = 0; i < 4; i++) {
      aiv[7 - i] = (byte) (m >>> (8 * i));
    }

    // Pad to 8-bytes
    byte[] padded;
    if ((m & 7) == 0) {
      // no padding required
      padded = plainText;
    } else {
      // some passing required
      int length = (plainText.length & ~7) + 8;
      padded = new byte[length];
      System.arraycopy(plainText, 0, padded, 0, plainText.length);
    }

    // break into 64-bit sections
    int n = padded.length / 8;
    byte[][] P = new byte[n][8];
    for (int i = 0; i < n; i++) {
      System.arraycopy(padded, i * 8, P[i], 0, 8);
    }

    // Special case: key is 8 bytes or less. In this case there is only a
    // single layer of ECB rather than the normal six.
    // This seems wrong to me, but it is what the standard says.
    if (n == 1) {
      wrapper.update(aiv);
      return wrapper.doFinal(P[0]);
    }

    // From the RFC
    //
    // Inputs: Plaintext, n 64-bit values {P1, P2, ..., Pn}, and Key, K (the
    // KEK).
    // Outputs: Ciphertext, (n+1) 64-bit values {C0, C1, ..., Cn}.
    //
    // 1) Initialize variables.
    //
    // Set A = IV, an initial value (see 2.2.3)
    // For i = 1 to n
    // R[i] = P[i]
    //
    // 2) Calculate intermediate values.
    //
    // For j = 0 to 5
    // For i=1 to n
    // B = AES(K, A | R[i])
    // A = MSB(64, B) ^ t where t = (n*j)+i
    // R[i] = LSB(64, B)
    //
    // 3) Output the results.
    //
    // Set C[0] = A
    // For i = 1 to n
    // C[i] = R[i]

    for (int j = 0; j < 6; j++) {
      for (int i = 0; i < n; i++) {
        wrapper.update(aiv);
        byte[] B = wrapper.doFinal(P[i]);

        // NB Add one to 't' as our 'R' is zero based, not 1 based
        System.arraycopy(B, 0, aiv, 0, 8);
        int t = n * j + i + 1;
        for (int k = 0; (k < 4) && (t != 0); k++) {
          aiv[7 - k] ^= t & 0xff;
          t >>>= 8;
        }
        System.arraycopy(B, 8, P[i], 0, 8);
      }
    }

    // Create the consolidated output and return it
    byte[] output = new byte[8 * (n + 1)];
    System.arraycopy(aiv, 0, output, 0, 8);
    for (int i = 0; i < n; i++) {
      System.arraycopy(P[i], 0, output, (i + 1) * 8, 8);
    }
    return output;
  }

}
