package prng;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entropy source that feeds into Fortuna.
 * 
 * @author Simon Greatrix
 *
 */
public class EntropySource {
    /** Generator for unique event wrapper sources */
    private static AtomicInteger EVENT_WRAPPER_ID_SRC = new AtomicInteger(1);

    /** Unique identifier for this source */
    private final int hashID;

    /**
     * This event source's "unique" id.
     */
    private final byte id;

    /**
     * The pool the event is to be written to
     */
    private int pool = 0;


    /**
     * New entropy source
     */
    public EntropySource() {
        hashID = EVENT_WRAPPER_ID_SRC.getAndIncrement();
        id = (byte) hashID;
    }


    @Override
    public boolean equals(Object other) {
        if( other == null ) return false;
        if( !(other instanceof EntropySource) ) return false;
        return hashID == ((EntropySource) other).hashID;
    }


    @Override
    public int hashCode() {
        return hashID;
    }


    /**
     * Post an event to the Fortuna instance
     * 
     * @param data
     *            the data to post
     */
    protected void post(byte[] data) {
        int myPool;
        synchronized (this) {
            pool = myPool = (pool + 1) % 32;
        }
        Fortuna.addEvent(myPool, data);
    }


    /**
     * Set an 8-bit of entropy event
     * 
     * @param b
     *            the entropy
     */
    public final void setEvent(byte b) {
        byte[] data = new byte[3];
        data[0] = id;
        data[1] = 1;
        data[2] = b;
        post(data);
    }


    /**
     * Set an event from an array of bytes. The maximum event size is 255 bytes.
     * Data beyond that will be ignored.
     * 
     * @param b
     *            the entropy
     */
    public final void setEvent(byte[] b) {
        int len = b.length;
        if( len > 255 ) {
            len = 255;
        }
        byte[] data = new byte[2 + len];
        data[0] = id;
        data[1] = (byte) len;
        System.arraycopy(b, 0, data, 2, len);
        post(data);
    }


    /**
     * Set a 32-bit of entropy event.
     * 
     * @param d
     *            the entropy
     */
    public final void setEvent(double d) {
        setEvent(Double.doubleToRawLongBits(d));
    }


    /**
     * Set a 32-bit of entropy event
     * 
     * @param f
     *            the entropy
     */
    public final void setEvent(float f) {
        setEvent(Float.floatToRawIntBits(f));
    }


    /**
     * Set a 32-bit of entropy event
     * 
     * @param i
     *            the entropy
     */
    public final void setEvent(int i) {
        byte[] data = new byte[6];
        data[0] = id;
        data[1] = 4;
        data[2] = (byte) (i >> 24);
        data[3] = (byte) (i >> 16);
        data[4] = (byte) (i >> 8);
        data[5] = (byte) i;
        post(data);
    }


    /**
     * Set a 64-bit of entropy event
     * 
     * @param l
     *            the entropy
     */
    public final void setEvent(long l) {
        byte[] data = new byte[10];
        data[0] = id;
        data[1] = 8;
        int i1 = (int) (l >>> 32);
        int i2 = (int) l;

        data[2] = (byte) (i1 >> 24);
        data[3] = (byte) (i1 >> 16);
        data[4] = (byte) (i1 >> 8);
        data[5] = (byte) i1;

        data[6] = (byte) (i2 >> 24);
        data[7] = (byte) (i2 >> 16);
        data[8] = (byte) (i2 >> 8);
        data[9] = (byte) i2;
        post(data);
    }


    /**
     * Set a 16-bit of entropy event
     * 
     * @param s
     *            the entropy
     */
    public final void setEvent(short s) {
        byte[] data = new byte[4];
        data[0] = id;
        data[1] = 2;
        data[2] = (byte) (s >> 8);
        data[3] = (byte) s;
        post(data);
    }
}
