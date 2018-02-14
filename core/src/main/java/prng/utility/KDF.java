package prng.utility;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implementation of NIST SP800-108 key derivation function in double pipeline mode.
 *
 * <p> Example usage:
 *
 * <p> <code> // privateKey and publicKey are compatible elliptic curve keys // kdfContext is some data known to both parties // // Calculate the key material
 * using elliptic curve Diffie-Hellman KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH"); keyAgreement.init(privateKey);
 * keyAgreement.doPhase(publicKey,true); byte[] secret = keyAgreement.generateSecret();
 *
 * // Derive the IV and key from the secret and the shared context byte[] iv = KDF.derive(secret,"IV",kdfContext,12); byte[] rawKey =
 * KDF.derive(secret,"KEY",kdfContext,32); SecretKey secretKey = new SecretKeySpec(rawKey,"AES");
 *
 * // Create a cipher in the normal way gcmSpec = new GCMParameterSpec(128, iv); cipher = Cipher.getInstance("AES/GCM/NoPadding");
 * cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec); </code>
 *
 * @author Simon Greatrix on 30/10/2017.
 */
public class KDF {

  /**
   * Derive a key from the provided inputs.
   *
   * @param keyMaterial the key material
   * @param label the label (which is converted using UTF16-BE)
   * @param context the context
   * @param length the number of bytes required
   *
   * @return the generated key data
   */
  public static byte[] derive(byte[] keyMaterial, String label,
      byte[] context, int length) {
    return derive(keyMaterial, label.getBytes(StandardCharsets.UTF_16BE),
        context, length);
  }


  /**
   * Derive a key from the provided inputs.
   *
   * @param keyMaterial the key material
   * @param label the label
   * @param context the context
   * @param length the number of bytes required
   *
   * @return the generated key data
   */
  public static byte[] derive(byte[] keyMaterial, byte[] label,
      byte[] context, int length) {
    final Mac mac;
    try {
      mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(keyMaterial, "HmacSHA256"));
    } catch (GeneralSecurityException e) {
      throw new Error("Impossible exception: HMAC SHA-256 is required",
          e);
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

    final byte[] output = new byte[length];

    // First pipe-line's input is the IV
    byte[] previous = iv;

    // The second pipe-line's input is Hmac || counter || IV, so add 36
    // bytes
    byte[] input = new byte[iv.length + 36];
    System.arraycopy(iv, 0, input, 36, iv.length);
    for (int i = 0; i * 32 < length; i++) {
      // Update first pipe-line
      previous = mac.doFinal(previous);

      // Create input to second pipe-line
      System.arraycopy(previous, 0, input, 0, previous.length);
      int j = i + 1;
      for (int k = 35; k >= 32; k--) {
        input[k] = (byte) (0xff & j);
        j >>>= 8;
      }

      // Update second pipe-line and copy to output
      byte[] block = mac.doFinal(input);
      int s = i * 32;
      if ((s + 32) < length) {
        // more blocks needed
        System.arraycopy(block, 0, output, s, 32);
      } else {
        // final block
        System.arraycopy(block, 0, output, s, length - s);
      }
    }

    return output;
  }
}