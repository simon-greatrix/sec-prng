package prng.internet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import prng.SecureRandomProvider;
import prng.config.Config;
import prng.seeds.SeedStorage;

/**
 * Fetch random data from well known on-line sources. Examples of web sources are:
 *
 * <ol> <li>www.random.org : Generates random data from radio static. <li>qrng.anu.edu.au : Generates random data from quantum vacuum fluctuations.
 * <li>www.fourmilab.ch/hotbits : Generates random data from the radioactive decay of Kr-85. </ol>
 *
 * Each service is only asked for 1024 bits (128 bytes) at a time.
 *
 * @author Simon Greatrix
 */
abstract public class NetRandom {

  /** Logger for this class */
  protected static final Logger LOG = LoggerFactory.getLogger(
      NetRandom.class);

  /** Number of milliseconds before a connection attempt times out */
  private static final int CONNECT_TIMEOUT;

  /** Number of milliseconds before a read attempt times out */
  private static final int READ_TIMEOUT;


  /**
   * Connect to a URL and set appropriate timeout parameters
   *
   * @param url the url to connect to
   *
   * @return the connection
   * @throws IOException if connecting to the server fails
   */
  protected static HttpURLConnection connect(URL url) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(CONNECT_TIMEOUT);
    conn.setReadTimeout(READ_TIMEOUT);
    conn.setUseCaches(false);
    conn.setDoInput(true);
    conn.setDoOutput(false);
    conn.connect();
    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
      conn.disconnect();
      throw new IOException(url.getHost() + " returned status "
          + conn.getResponseCode());
    }
    return conn;
  }


  /**
   * Connect to a URL and set appropriate timeout parameters
   *
   * @param url the url to connect to
   * @param request the JSON-RPC request
   *
   * @return the connection
   * @throws IOException if connecting to the server or reading the response fails
   */
  protected static byte[] connectRPC(URL url, byte[] request)
      throws IOException {
    // create the connection to post json-rpc
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(120000);
    conn.setReadTimeout(120000);
    conn.setUseCaches(false);
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type",
        "application/json-rpc; charset=us-ascii");
    conn.setRequestProperty("Content-Length",
        Integer.toString(request.length));
    conn.connect();

    // send the JSON request
    OutputStream out = null;
    try {
      out = conn.getOutputStream();
      out.write(request);
      out.flush();
    } catch (IOException ioe) {
      if (out != null) {
        out.close();
      }
      conn.disconnect();
      throw ioe;
    }

    // get the response
    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
      conn.disconnect();
      throw new IOException(url.getHost() + " returned status "
          + conn.getResponseCode());
    }

    // read the data
    byte[] data;
    try {
      data = read(conn);
    } finally {
      conn.disconnect();
    }

    return data;
  }


  /**
   * Read the response from an HTTP connection
   *
   * @param conn the connection
   *
   * @return the response
   * @throws IOException if communicating with the service fails
   */
  protected static byte[] read(HttpURLConnection conn) throws IOException {
    byte[] buffer = new byte[1024];
    int pos = 0;
    try (InputStream in = conn.getInputStream()) {
      int r;
      while ((r = in.read()) != -1) {
        if (pos < 1024) {
          buffer[pos] = (byte) r;
        } else {
          break;
        }
        pos++;
      }
    }

    // copy output into appropriate sized array
    byte[] output = new byte[pos];
    System.arraycopy(buffer, 0, output, 0, pos);
    return output;
  }

  static {
    Config config = Config.getConfig("network");
    CONNECT_TIMEOUT = config.getInt("connectionTimeOut", 120000);
    READ_TIMEOUT = config.getInt("readTimeOut", 120000);
  }


  /**
   * Fetch data from the internet source, if possible
   *
   * @return the entropy, a 128 byte value
   * @throws IOException if fetch failed
   */
  abstract byte[] fetch() throws IOException;


  /**
   * Load some new entropy.
   *
   * @return the entropy, or an empty byte array
   */
  public byte[] load() {
    byte[] newData;
    try {
      newData = AccessController.doPrivileged(
          new PrivilegedExceptionAction<byte[]>() {
            @Override
            public byte[] run() throws IOException {
              return fetch();
            }
          });
      if (newData == null || newData.length != 128) {
        // Failed to fetch data. It happens.
        LOG.warn("Invalid data received. Got {} bytes instead of 128",
            newData == null ? "null"
                : Integer.toString(newData.length));

        // blank the entropy to indicate it is no good
        newData = new byte[0];
      }
    } catch (PrivilegedActionException e) {
      Exception ioe = e.getException();
      // Failed to fetch data. It happens.
      LOG.warn("External entropy service failed", ioe);

      // blank the entropy to indicate it is no good
      newData = new byte[0];
    } catch (SecurityException e) {
      SecureRandomProvider.LOG.warn(
          "Lacking permission \"SocketPermission {} resolve,connect\" or \"URLPermission {} GET,POST\". Cannot access to internet entropy source",
          url(), url());
      newData = new byte[0];
    }

    return SeedStorage.scramble(newData);
  }


  /**
   * The URL this service connects to.
   *
   * @return the URL
   */
  abstract URL url();
}
