package prng.internet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.NonceFactory;

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
abstract public class NetRandom {
    /** Logger for this class */
    protected static final Logger LOG = LoggerFactory.getLogger(NetRandom.class);

    /** Default time. "Default" in ASCII */
    private static final long TIME_DEFAULT = -0x44656661756c74l;

    /** Unset time. "Unset" in ASCII */
    private static final long TIME_UNSET = -0x556e736574l;


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
        conn.setConnectTimeout(120000);
        conn.setReadTimeout(120000);
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

    /** The actual entropy */
    private byte[] entropy_ = new byte[0];

    /**
     * The time the entropy was loaded. Old entropy can be replaced. The initial
     * value is ASCII for "Unset".
     */
    private long loadTime_ = TIME_UNSET;

    /** This source's name */
    private final String name_;

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
        name_ = "NetRandom/" + name;
        sync();
    }


    /**
     * Fetch data from the internet source, if possible
     * 
     * @return the entropy
     * @throws IOException
     *             if fetch failed
     */
    abstract byte[] fetch() throws IOException;


    /**
     * Get entropy from this source.
     * 
     * @param output
     * @param offset
     * @param length
     * @return
     */
    public synchronized int getEntropy(byte[] output, int offset, int length) {
        sync();
        if( entropy_.length == 0 ) return 0;

        int pos = position_;
        int rem = 128 - pos;

        // if we have enough entropy, use some of what we have now
        if( length <= rem ) {
            System.arraycopy(entropy_, offset, output, pos, length);
            position_ += length;
            return length;
        }

        // use up what we have
        System.arraycopy(entropy_, offset, output, pos, rem);
        offset += rem;
        int toDo = length - rem;

        // for as many full blocks as we need, scramble and copy
        while( toDo > 128 ) {
            scramble();
            usageCount_++;
            System.arraycopy(entropy_, offset, output, 0, 128);
            offset += 128;
            toDo -= 128;
        }

        // scramble and copy the final part
        scramble();
        usageCount_++;
        System.arraycopy(entropy_, offset, output, 0, toDo);
        position_ = toDo;

        // save new usage count
        Preferences prefs = Preferences.userNodeForPackage(NetRandom.class);
        prefs = prefs.node(name_);
        try {
            prefs.sync();
            long savedLoadTime = prefs.getLong("loadTime", TIME_DEFAULT);
            int savedUsageCount = prefs.getInt("usageCount", 0);
            if( savedLoadTime == loadTime_ && savedUsageCount < usageCount_ ) {
                prefs.putInt("usageCount", usageCount_);
                prefs.flush();
            }
        } catch (BackingStoreException e) {
            BMLog.log(BMLog.COMPONENT_SYSTEM, 1, "External storage failed", e);
        }

        return length;
    }


    /**
     * Refresh from source if necessary
     */
    private void refresh() {
        long age = System.currentTimeMillis() - loadTime_;
        // must refresh if no entropy
        boolean mustRefresh = (entropy_.length != 128);

        // must refresh if older than a week
        if( age > 604800000 ) mustRefresh = true;

        // must refresh if older than a day and used more than 32 times
        if( (age > 86400000) && (usageCount_ >= 32) ) mustRefresh = true;

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

        entropy_ = newData;
        usageCount_ = 0;
        loadTime_ = System.currentTimeMillis();
        scramble();

        // save new entropy
        Preferences prefs = Preferences.userNodeForPackage(NetRandom.class);
        prefs = prefs.node(name_);

        prefs.putByteArray("entropy", entropy_);
        prefs.putLong("loadTime", loadTime_);
        prefs.getInt("usageCount", 0);

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            BMLog.log(BMLog.COMPONENT_SYSTEM, 1, "External storage failed", e);
        }
    }


    /**
     * Scramble the current entropy
     */
    private void scramble() {
        if( entropy_.length == 0 ) return;

        // We encrypt the entropy using AES. This preserves all information but
        // produces a completely new bit representation which will cause all
        // cryptographic objects generated from it to be unique.
        try {
            byte[] nonce = NonceFactory.create();
            SecretKeySpec key = new SecretKeySpec(nonce, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            entropy_ = cipher.doFinal(entropy_);
        } catch (GeneralSecurityException e) {
            throw new Error("Cryptographic failure", e);
        }
    }


    /**
     * Sync this instance with the stored entropy
     */
    private void sync() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(NetRandom.class);
            prefs = prefs.node(name_);
            prefs.sync();
            if( prefs.nodeExists("entropy") ) {
                // The default value is ASCII for "Default"
                long savedLoadTime = prefs.getLong("loadTime", TIME_DEFAULT);
                int savedUsageCount = prefs.getInt("usageCount", 0);

                // do not reload entropy if it is the same value
                if( savedLoadTime != loadTime_ && savedLoadTime != TIME_DEFAULT ) {
                    entropy_ = prefs.getByteArray("entropy", entropy_);
                    scramble();
                    loadTime_ = savedLoadTime;
                    usageCount_ = savedUsageCount;
                } else if( usageCount_ < savedUsageCount ) {
                    usageCount_ = savedUsageCount;
                }
            }
        } catch (BackingStoreException e) {
            entropy_ = new byte[0];
            loadTime_ = TIME_UNSET;
            usageCount_ = 0;
        }

        position_ = 0;

        refresh();
    }


    @Override
    public String toString() {
        return "NetRandom [loadTime=" + loadTime_ + ", name=" + name_
                + ", position=" + position_ + ", usageCount=" + usageCount_
                + "]";
    }

}
