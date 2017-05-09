package prng.utilities;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

import javax.crypto.Mac;

/**
 * Input stream with MAC updated on every read
 *
 * @author Simon Greatrix
 *
 */
public class MacInputStream extends FilterInputStream {

    /** CRC32 for zip file */
    private final CRC32 crc = new CRC32();

    /** Message Authentication Code calculator */
    private final Mac mac;


    /**
     * New instance
     *
     * @param in
     *            input stream
     * @param mac
     *            MAC instance
     */
    public MacInputStream(InputStream in, Mac mac) {
        super(in);
        this.mac = mac;
    }


    /**
     * Get the CRC-32 of the input
     *
     * @return the CRC
     */
    public long getCRC() {
        return crc.getValue();
    }


    @Override
    public int read() throws IOException {
        int r = super.read();
        if( r != -1 ) {
            mac.update((byte) r);
            crc.update(r);
        }
        return r;
    }


    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int r = super.read(b, off, len);
        if( r != -1 ) {
            mac.update(b, off, r);
            crc.update(b, off, r);
        }
        return r;
    }
}
