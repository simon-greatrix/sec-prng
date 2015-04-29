package prng.internet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.Config;
import prng.seeds.Seed;
import prng.seeds.SeedInput;
import prng.seeds.SeedOutput;
import prng.seeds.SeedStorage;

/**
 * Fetch random data from well known on-line sources. The web sources are:
 * 
 * <ol>
 * <li>www.random.org : Generates random data from radio static.
 * <li>qrng.anu.edu.au : Generates random data from quantum vacuum fluctuations.
 * <li>www.fourmilab.ch/hotbits : Generates random data from the radioactive
 * decay of Kr-85.
 * </ol>
 *
 * Each service is only asked for 1024 bits (128 bytes) at a time.
 * 
 * @author Simon Greatrix
 *
 */
abstract public class NetRandom extends Seed {
    /** Logger for this class */
    protected static final Logger LOG = LoggerFactory.getLogger(NetRandom.class);

    /** Unset time. Negative of "Unset" in ASCII */
    private static final long TIME_UNSET = -0x556e736574l;
    
    /** Minimum age before entropy is refreshed */    
    private static final long MIN_AGE;
    
    /** Maximum age after which entropy must be refreshed */
    private static final long MAX_AGE;
    
    /** Minimum number of uses before entropy is refreshed */
    private static final int MIN_USAGE;
    
    /** Number of milliseconds before a connection attempt times out */
    private static final int CONNECT_TIMEOUT;
    
    /** Number of milliseconds before a read attempt times out */
    private static final int READ_TIMEOUT;
    
    static {
        Config config = Config.getConfig("expiry",NetRandom.class);
        MIN_AGE = config.getLong("minAge", TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));
        MAX_AGE = config.getLong("maxAge", TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS));
        MIN_USAGE = config.getInt("minUsage", 32);
        CONNECT_TIMEOUT = config.getInt("connectionTimeOut",120000); 
        READ_TIMEOUT = config.getInt("readTimeOut",120000); 
    }


    /**
     * Connect to a URL and set appropriate timeout parameters
     * 
     * @param url
     *            the url to connect to
     * @return the connection
     * @throws IOException
     */
    protected static HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(false);
        conn.connect();
        if( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
            conn.disconnect();
            throw new IOException(url.getHost() + " returned status "
                    + conn.getResponseCode());
        }
        return conn;
    }


    /**
     * Connect to a URL and set appropriate timeout parameters
     * 
     * @param url
     *            the url to connect to
     * @param request
     *            the JSON-RPC request
     * @return the connection
     * @throws IOException
     */
    protected static byte[] connectRPC(URL url, byte[] request) throws IOException {
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
            if( out != null ) out.close();
            conn.disconnect();
            throw ioe;
        }

        // get the response
        if( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
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
     * @param conn
     *            the connection
     * @return the response
     * @throws IOException
     */
    protected static byte[] read(HttpURLConnection conn) throws IOException {
        byte[] buffer = new byte[1024];
        int pos = 0;
        try (InputStream in = conn.getInputStream()) {
            int r;
            while( (r = in.read()) != -1 ) {
                if( pos < 1024 ) {
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

    /**
     * The time the entropy was loaded. Old entropy can be replaced. The initial
     * value is ASCII for "Unset".
     */
    private long loadTime_ = TIME_UNSET;

    /** Current position */
    private int position_;

    /** Number of times this entropy has been used */
    private int usageCount_ = 0;


    /**
     * NetRandom associated with the named source
     * 
     * @param name
     *            the source
     */
    protected NetRandom(String name) {
        super("NetRandom/" + name, new byte[0]);
    }


    /**
     * Fetch data from the internet source, if possible
     * 
     * @return the entropy, a 128 byte value
     * @throws IOException
     *             if fetch failed
     */
    abstract byte[] fetch() throws IOException;


    /**
     * Get 256-bit entropy from this source.
     * 
     * @return seed bytes
     */
    public byte[] getSeed() {
        synchronized (this) {
            refresh();
            if( data_ == null || data_.length == 0 ) return new byte[0];

            byte[] output = new byte[32];
            int pos = position_;
            int rem = 128 - pos;

            // if we have enough entropy, use some of what we have now
            if( 32 <= rem ) {
                System.arraycopy(data_, 0, output, pos, 32);
                position_ += 32;
                return output;
            }

            // scramble to create new blocks
            data_ = SeedStorage.scramble(data_);
            usageCount_++;
            position_ = 32;
            System.arraycopy(data_, 0, output, 0, 32);
            save();
            return output;
        }
    }


    public NetRandom() {
        // do nothing
    }


    @Override
    public void initialize(SeedInput input) throws Exception {
        super.initialize(input);
        loadTime_ = input.readLong();
        usageCount_ = input.readInt();
        position_ = input.readInt();
    }


    @Override
    public void save(SeedOutput output) {
        synchronized (this) {
            super.save(output);
            output.writeLong(loadTime_);
            output.writeInt(usageCount_);
            output.writeInt(position_);
        }
    }


    /**
     * Refresh from source if necessary
     */
    private void refresh() {
        long age = System.currentTimeMillis() - loadTime_;
        // must refresh if no entropy
        boolean mustRefresh = (data_.length != 128);

        // must refresh if older than a week
        if( age > MAX_AGE ) mustRefresh = true;

        // must refresh if older than a day and used more than 32 times
        if( (age > MIN_AGE) && (usageCount_ >= MIN_USAGE) ) mustRefresh = true;

        if( !mustRefresh ) return;
        byte[] newData;
        try {
            newData = fetch();
            if( newData == null || newData.length != 128 )
                throw new IOException("Invalid data returned");
        } catch (IOException ioe) {
            // Failed to fetch data. It happens.
            LOG.warn("External entropy service failed", ioe);

            // blank the entropy to indicate it is no good
            newData = new byte[0];
        }

        data_ = SeedStorage.scramble(newData);
        usageCount_ = 0;
        loadTime_ = System.currentTimeMillis();
        save();
    }


    @Override
    public String toString() {
        return "NetRandom [loadTime=" + loadTime_ + ", name=" + getName()
                + ", position=" + position_ + ", usageCount=" + usageCount_
                + "]";
    }

}
