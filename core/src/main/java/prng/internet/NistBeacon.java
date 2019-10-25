package prng.internet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestException;
import java.security.MessageDigest;
import prng.generator.HashSpec;

/**
 * @author Simon Greatrix on 25/10/2019.
 */
public class NistBeacon extends NetRandom {

  private static final URL NIST_BEACON;

  static {
    try {
      NIST_BEACON = new URL("https://beacon.nist.gov/beacon/2.0/pulse/last");
    } catch (MalformedURLException e) {
      throw new InternalError("Impossible exception", e);
    }
  }

  @Override
  byte[] fetch() throws IOException {
    MessageDigest digest1 = HashSpec.SPEC_SHA512.getInstance();
    MessageDigest digest2 = HashSpec.SPEC_SHA512.getInstance();

    digest1.update((byte) 0);
    digest2.update((byte) 255);

    byte[] result = new byte[128];
    HttpURLConnection conn = connect(NIST_BEACON);
    try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
      int r;
      while ((r = in.read()) != -1) {
        byte b = (byte) r;
        digest1.update(b);
        digest2.update(b);
      }
    } finally {
      conn.disconnect();
    }

    try {
      digest1.digest(result, 0, 64);
      digest2.digest(result, 64, 64);
    } catch (DigestException e) {
      throw new InternalError("Impossible exception", e);
    }
    return result;
  }


  @Override
  URL url() {
    return NIST_BEACON;
  }
}
