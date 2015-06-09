package prng.internet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import prng.internet.SimpleJSONParser.JSONArray;
import prng.internet.SimpleJSONParser.JSONObject;
import prng.internet.SimpleJSONParser.Primitive;
import prng.internet.SimpleJSONParser.Type;
import prng.utility.BLOBPrint;

/**
 * Source that fetches from the ANU QRNG service. Note that although the ANU
 * QRNG service offers an HTTPS API, the HTTPS certificates contain forbidden
 * extensions and therefore do not validate.
 * 
 * @author Simon Greatrix
 *
 */
public class Quantum extends NetRandom {
    /** Service URL */
    private static final URL QRNG;

    static {
        try {
            QRNG = new URL(
                    "http://qrng.anu.edu.au/API/jsonI.php?length=128&size=1&type=uint8");
        } catch (MalformedURLException e) {
            throw new Error("Impossible exception", e);
        }
    }


    /**
     * Read data from the Australian National University Quantum Random Number
     * Generator.
     * 
     * @return the bits
     * @throws IOException
     */
    byte[] fetch() throws IOException {
        HttpURLConnection conn = connect(QRNG);
        byte[] data;
        try {
            data = read(conn);
        } finally {
            conn.disconnect();
        }

        try {
            // convert response to JSON
            Primitive result = SimpleJSONParser.parse(new InputStreamReader(
                    new ByteArrayInputStream(data), StandardCharsets.ISO_8859_1));
            if( result.getType() != Type.OBJECT ) {
                throw new IOException(QRNG.getHost() + " returned JSON type: "
                        + result.getType());
            }
            JSONObject obj = result.getValueSafe(JSONObject.class);

            // response will indicate success
            if( !obj.get(Boolean.class, "success", Boolean.FALSE).booleanValue() ) {
                throw new IOException(QRNG.getHost()
                        + " did not indicate success");
            }

            // if successful, should contain data array
            JSONArray arr = obj.get(JSONArray.class, "data", null);
            if( arr == null ) {
                throw new IOException(QRNG.getHost() + " did not return data");
            }
            if( arr.size() != 128 ) {
                throw new IOException(QRNG.getHost() + " returned "
                        + arr.size() + " bytes not 128");
            }

            // load data into array
            byte[] bits = new byte[128];
            int pos = 0;
            for(Primitive prim:arr) {
                Integer val = prim.getValue(Integer.class, null);
                if( val == null ) {
                    throw new IOException(QRNG.getHost() + " sent data of "
                            + prim.getType() + ": " + prim
                            + " which is not an integer");
                }
                bits[pos] = val.byteValue();
                pos++;
            }

            // done, return data
            return bits;
        } catch (IOException ioe) {
            LOG.error("Bad data received from {}\n\n{}", QRNG.getHost(),
                    BLOBPrint.toString(data));
            throw ioe;
        }
    }
}