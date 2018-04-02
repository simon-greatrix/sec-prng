package prng.internet;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Source that fetches from the Fourmilab HotBits service
 *
 * @author Simon Greatrix
 */
public class HotBits extends NetRandom {

  /** URL for Hot Bits service */
  private static final URL HOT_BITS;

  static {
    try {
      // https://www.fourmilab.ch/cgi-bin/Hotbits.api?nbytes=128&fmt=bin&apikey=HB1RNFWeh9e8HTTZm06b5sRSUPU
      HOT_BITS = new URL(
          "https://www.fourmilab.ch/cgi-bin/Hotbits?nbytes=128&fmt=bin&apikey=Pseudorandom");
    } catch (MalformedURLException e) {
      throw new InternalError("Impossible exception", e);
    }
  }


  /**
   * Read data from Fourmilab's Hot Bits service
   *
   * @return the bits
   * @throws IOException if communication with the service goes wrong
   */
  @Override
  byte[] fetch() throws IOException {
    HttpURLConnection conn = connect(HOT_BITS);
    try {
      byte[] data = read(conn);
      if (data.length != 128) {
        throw new IOException(HOT_BITS.getHost()
            + " returned " + data.length + " bytes, not 128");
      }
      return data;
    } finally {
      conn.disconnect();
    }
  }


  @Override
  URL url() {
    return HOT_BITS;
  }

}