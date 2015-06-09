package prng.internet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import prng.internet.SimpleJSONParser.JSONArray;
import prng.internet.SimpleJSONParser.JSONObject;
import prng.internet.SimpleJSONParser.Primitive;
import prng.internet.SimpleJSONParser.Type;
import prng.utility.BLOBPrint;
import prng.utility.Config;

/**
 * Source that fetches from the Random.Org service
 * 
 * @author Simon Greatrix
 *
 */
public class RandomDotOrg extends NetRandom {
    /** URL of random number services */
    private static final URL RANDOM_DOT_ORG;

    /** JSON request sent to random.org */
    private static final byte[] RANDOM_REQUEST;

    static {
        try {
            RANDOM_DOT_ORG = new URL("https://api.random.org/json-rpc/1/invoke");
        } catch (MalformedURLException e) {
            throw new Error("Impossible exception", e);
        }

        Config config = Config.getConfig("", RandomDotOrg.class);
        String apiKey = config.get("apiKey");
        if( apiKey != null ) {
            NetRandom.LOG.info("random.org RNG using API key :" + apiKey);
            JSONObject obj = new JSONObject();
            obj.put("jsonrpc", new Primitive(Type.STRING, "2.0"));
            obj.put("method", new Primitive(Type.STRING, "generateIntegers"));
            JSONObject params = new JSONObject();
            obj.put("params", new Primitive(Type.OBJECT, params));
            obj.put("id", new Primitive(Type.STRING, "seed"));
            params.put("apiKey", new Primitive(Type.STRING, apiKey));
            params.put("n", new Primitive(Type.NUMBER, Integer.valueOf(128)));
            params.put("min", new Primitive(Type.NUMBER, Integer.valueOf(0)));
            params.put("max", new Primitive(Type.NUMBER, Integer.valueOf(255)));
            RANDOM_REQUEST = obj.toString().getBytes(StandardCharsets.US_ASCII);
        } else {
            NetRandom.LOG.info("random.org RNG not in use as no API key provided");
            RANDOM_REQUEST = null;
        }
    }


    /**
     * Read data from random.org's service.
     * 
     * @return the bits
     * @throws IOException
     */
    byte[] fetch() throws IOException {
        if( RANDOM_REQUEST == null ) return new byte[0];
        byte[] data = connectRPC(RANDOM_DOT_ORG, RANDOM_REQUEST);

        try {
            Primitive result = SimpleJSONParser.parse(new InputStreamReader(
                    new ByteArrayInputStream(data), StandardCharsets.ISO_8859_1));
            if( result.getType() != Type.OBJECT ) {
                throw new IOException(RANDOM_DOT_ORG.getHost()
                        + " returned JSON type: " + result.getType());
            }
            JSONObject obj = result.getValueSafe(JSONObject.class);
            JSONObject res = obj.get(JSONObject.class, "result", null);
            JSONObject err = obj.get(JSONObject.class, "error", null);

            // check for an explicit error
            if( err != null ) {
                String msg = err.get(String.class, "message", null);
                if( msg == null ) msg = err.toString();
                throw new IOException(RANDOM_DOT_ORG.getHost() + ": " + msg);
            }

            // if no error, a result is required
            if( res == null ) {
                throw new IOException(RANDOM_DOT_ORG.getHost()
                        + ": no results returned\n" + result.toString());
            }

            // result should contain a "random" result
            res = res.get(JSONObject.class, "random", null);
            if( res == null ) {
                throw new IOException(RANDOM_DOT_ORG.getHost()
                        + ": no \"random\" in results\n" + result.toString());
            }

            // and the "random" object should contain the actual data
            JSONArray randData = res.get(JSONArray.class, "data", null);
            if( randData == null ) {
                throw new IOException(RANDOM_DOT_ORG.getHost()
                        + ": no data in results\n" + result.toString());
            }
            if( randData.size() != 128 ) {
                throw new IOException(RANDOM_DOT_ORG.getHost() + " returned "
                        + randData.size() + " bytes not 128");
            }

            // get the bytes from the JSON
            byte[] bits = new byte[128];
            int pos = 0;
            for(Primitive prim:randData) {
                Integer val = prim.getValue(Integer.class, null);
                if( val == null ) {
                    throw new IOException(RANDOM_DOT_ORG.getHost()
                            + " sent data of " + prim.getType() + ": " + prim
                            + " which is not an integer");
                }
                bits[pos] = val.byteValue();
                pos++;
            }
            return bits;
        } catch (IOException ioe) {
            LOG.error("Bad data received from {}\n\n{}",
                    RANDOM_DOT_ORG.getHost(), BLOBPrint.toString(data));
            throw ioe;
        }
    }

}