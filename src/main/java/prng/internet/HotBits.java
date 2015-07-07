package prng.internet;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Source that fetches from the Fourmilab HotBits service
 * 
 * @author Simon Greatrix
 *
 */
public class HotBits extends NetRandom {
    /** URL for Hot Bits service */
    private static final URL HOT_BITS;

    static {
        try {
            HOT_BITS = new URL(
                    "https://www.fourmilab.ch/cgi-bin/Hotbits?nbytes=128&fmt=bin");
        } catch (MalformedURLException e) {
            throw new Error("Impossible exception", e);
        }
    }


    /**
     * Read data from Fourmilab's Hot Bits service
     * 
     * @return the bits
     * @throws IOException if communication with the service goes wrong
     */
    byte[] fetch() throws IOException {
        HttpURLConnection conn = connect(HOT_BITS);
        try {
            byte[] data = read(conn);
            if( data.length != 128 )
                throw new IOException(HOT_BITS.getHost() + " returned "
                        + data.length + " bytes, not 128");
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