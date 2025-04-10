package prng.utilities;

import java.io.Console;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;

/**
 * Decrypt files previously encrypts with PBEFileEncrypt
 *
 * @author Simon Greatrix
 */
public class PBEFileDecrypt {

  /** Resource name used for data */
  public static final String DATA_RESOURCE = "﹏﹏﹏﹏﹏︳DATA︳%x︳﹏﹏﹏﹏﹏";

  /** Resource name used for meta information */
  public static final String META_RESOURCE = "﹏﹏﹏﹏﹏︳META︳%x︳﹏﹏﹏﹏﹏";


  /**
   * Get the password from the console.
   *
   * @return the password
   */
  static char[] getPassword() {
    Console cons = System.console();
    if (cons == null) {
      return "password".toCharArray();
    }
    char[] password;
    while (true) {
      password = cons.readPassword("Enter password (ASCII only) : ");
      char[] check = cons.readPassword("Confirm password            : ");
      if (!Arrays.equals(password, check)) {
        cons.format("\nPasswords differ. Please re-enter.\n\n");
      } else {
        Arrays.fill(check, '\0');
        break;
      }
    }
    cons.format("Password accepted.\n");
    return password;
  }


  /**
   * Application entry point
   *
   * @param args command line arguments
   *
   * @throws IOException              if the file cannot be read
   * @throws GeneralSecurityException if decryption fails
   */
  public static void main(String[] args)
      throws IOException, GeneralSecurityException {
    PBEFileDecrypt instance = new PBEFileDecrypt();
    instance.exec();
  }


  /**
   * Read the unencrypted metadata
   *
   * @return the metadata
   */
  private static PBEItem readMeta0() throws IOException {
    String name = "/" + String.format(META_RESOURCE, 0);
    try (InputStream in = PBEFileDecrypt.class.getResourceAsStream(name)) {
      DataInputStream dataIn = new DataInputStream(in);
      dataIn.readInt();
      return new PBEItem(dataIn);
    }
  }


  /** The password */
  char[] password;


  /** New instance. */
  private PBEFileDecrypt() {
  }


  /**
   * Run the application
   */
  private void exec() throws IOException, GeneralSecurityException {
    password = getPassword();
    PBEItem meta0 = readMeta0();
    PBEItem[] items = readMeta1(meta0);
    for (int i = 0; i < items.length; i++) {
      readData(i, items[i]);
    }
    Arrays.fill(password, '\0');
  }


  /**
   * Read a data file and write it out
   *
   * @param i       the file's index
   * @param pbeItem the associated meta data
   */
  private void readData(int i, PBEItem pbeItem)
      throws IOException, GeneralSecurityException {
    String name = "/" + String.format(DATA_RESOURCE, i);
    Cipher cipher = pbeItem.createCipher(password, Cipher.DECRYPT_MODE);
    try (InputStream in = PBEFileDecrypt.class.getResourceAsStream(name)) {
      Mac mac = pbeItem.getMac();
      InputStream dataIn = new MacInputStream(
          new GZIPInputStream(new CipherInputStream(in, cipher)),
          mac
      );

      File outFile = new File(pbeItem.getPath());
      try (OutputStream out = new FileOutputStream(outFile)) {
        int r;
        byte[] buffer = new byte[0x10000];
        while ((r = dataIn.read(buffer)) != -1) {
          out.write(buffer, 0, r);
        }
      }

      // Check the MAC of the meta information
      byte[] macActual = mac.doFinal();
      byte[] macExp = pbeItem.getExpectedMac();
      if (!Arrays.equals(macActual, macExp)) {
        System.err.println("\n\nData corruption detected for "
            + pbeItem.getPath() + "\n\n" + "MAC expected : "
            + PBEItem.toHex(macExp) + "\nMAC observed : "
            + PBEItem.toHex(macActual) + "\n\n");
        System.exit(1);
      }
    }
  }


  /**
   * Read the encrypted metadata
   *
   * @param meta0 the PBE specification for the encrypted metadata
   *
   * @return the decrypted meta data
   */
  private PBEItem[] readMeta1(PBEItem meta0)
      throws IOException, GeneralSecurityException {
    String name = "/" + String.format(META_RESOURCE, 1);
    Cipher cipher = meta0.createCipher(password, Cipher.DECRYPT_MODE);
    try (InputStream in = PBEFileDecrypt.class.getResourceAsStream(name)) {
      Mac mac = meta0.getMac();
      DataInputStream dataIn = new DataInputStream(
          new MacInputStream(new CipherInputStream(in, cipher), mac));
      int count = dataIn.readInt();
      PBEItem[] items = new PBEItem[count];
      for (int i = 0; i < count; i++) {
        items[i] = new PBEItem(dataIn);
      }

      // Check the MAC of the meta information
      byte[] macActual = mac.doFinal();
      byte[] macExp = meta0.getExpectedMac();
      if (!Arrays.equals(macActual, macExp)) {
        System.err.println(
            "\n\nData corruption detected in meta information.\n\n"
                + "MAC expected : " + PBEItem.toHex(macExp)
                + "\nMAC observed : " + PBEItem.toHex(macActual)
                + "\n\n");
        System.exit(1);
      }

      return items;
    }

  }

}
