package prng.utility;

import java.io.DataOutput;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

/**
 * An output stream that only calculates a digest. To ensure the digest is
 * robust, the input to this class should be such that if the bytes were stored
 * and later read back, the original data could be reconstructed.
 * 
 * @author Simon Greatrix
 *
 */
public class DigestDataOutput implements DataOutput {

    /** Buffer for numeric output */
    private byte[] buffer_ = new byte[8];

    /** The digest */
    private final MessageDigest digest_;


    /**
     * Create a new digest output
     * 
     * @param digest
     *            the digest instance to use
     */
    public DigestDataOutput(MessageDigest digest) {
        digest_ = digest;
        digest_.reset();
    }


    /**
     * Create a new digest output
     * 
     * @param name
     *            the required digest type
     */
    public DigestDataOutput(String name) {
        try {
            digest_ = MessageDigest.getInstance(name);
        } catch (NoSuchAlgorithmException e) {
            throw new Error(
                    "Digest algorithm \"" + name + "\" is not supported.");
        }
    }


    /**
     * Create a new digest output
     * 
     * @param name
     *            the required digest type
     * @param provider
     *            specific provider for algorithm
     */
    public DigestDataOutput(String name, Provider provider) {
        try {
            digest_ = MessageDigest.getInstance(name, provider);
        } catch (NoSuchAlgorithmException e) {
            throw new Error("Digest algorithm \"" + name
                    + "\" is not supported by provider \"" + provider.getName()
                    + "\".");
        }
    }


    /**
     * Calculate the digest. This also resets the digest instance.
     * 
     * @return the digest
     */
    public byte[] digest() {
        return digest_.digest();
    }


    @Override
    public void write(byte[] b) {
        digest_.update(b);
    }


    @Override
    public void write(byte[] b, int off, int len) {
        digest_.update(b, off, len);
    }


    @Override
    public void write(int b) {
        digest_.update((byte) b);
    }


    @Override
    public void writeBoolean(boolean v) {
        digest_.update(v ? (byte) 1 : (byte) 0);
    }


    @Override
    public void writeByte(int v) {
        digest_.update((byte) v);
    }


    @Override
    public void writeBytes(String s) {
        int l = s.length();
        byte[] buf = new byte[l];
        for(int i = 0;i < l;i++) {
            buf[i] = (byte) s.charAt(i);
        }
        digest_.update(buf);
    }


    @Override
    public void writeChar(int v) {
        buffer_[0] = (byte) (v >>> 8);
        buffer_[1] = (byte) v;
        digest_.update(buffer_, 0, 2);
    }


    @Override
    public void writeChars(String s) {
        int l = s.length();
        byte[] buf = new byte[l * 2];
        for(int i = 0;i < l;i++) {
            buf[i * 2] = (byte) (s.charAt(i) >>> 8);
            buf[i * 2 + 1] = (byte) s.charAt(i);
        }
        digest_.update(buf);
    }


    @Override
    public void writeDouble(double v) {
        writeLong(Double.doubleToLongBits(v));
    }


    @Override
    public void writeFloat(float v) {
        writeInt(Float.floatToIntBits(v));
    }


    @Override
    public void writeInt(int v) {
        buffer_[0] = (byte) (v >>> 24);
        buffer_[1] = (byte) (v >>> 16);
        buffer_[2] = (byte) (v >>> 8);
        buffer_[3] = (byte) v;
        digest_.update(buffer_, 0, 4);
    }


    @Override
    public void writeLong(long v) {
        buffer_[0] = (byte) (v >>> 56);
        buffer_[1] = (byte) (v >>> 48);
        buffer_[2] = (byte) (v >>> 40);
        buffer_[3] = (byte) (v >>> 32);
        buffer_[4] = (byte) (v >>> 24);
        buffer_[5] = (byte) (v >>> 16);
        buffer_[6] = (byte) (v >>> 8);
        buffer_[7] = (byte) v;
        digest_.update(buffer_, 0, 8);
    }


    @Override
    public void writeShort(int v) {
        buffer_[0] = (byte) (v >>> 8);
        buffer_[1] = (byte) v;
        digest_.update(buffer_, 0, 2);
    }


    /**
     * As defined in DataOutput, writes the supplied String out as modified
     * UTF-8. Unlike DataOutput, there is no length prefix. Instead a zero byte
     * is appended. This allows Strings of any length to be handled.
     * 
     * @param str
     *            the String to output
     */
    @Override
    public void writeUTF(String str) {
        byte[] buf = new byte[260];
        int len = str.length();
        int pos = 0;

        for(int i = 0;i < len;i++) {
            char ch = str.charAt(i);

            // output character in modified UTF-8
            if( (ch >= 0x0001) && (ch <= 0x007F) ) {
                buf[pos++] = (byte) ch;
            } else if( ch > 0x07FF ) {
                buf[pos++] = (byte) (0xE0 | ((ch >> 12) & 0x0F));
                buf[pos++] = (byte) (0x80 | ((ch >> 6) & 0x3F));
                buf[pos++] = (byte) (0x80 | ((ch >> 0) & 0x3F));
            } else {
                buf[pos++] = (byte) (0xC0 | ((ch >> 6) & 0x1F));
                buf[pos++] = (byte) (0x80 | ((ch >> 0) & 0x3F));
            }

            // if buffer full, flush it
            if( pos >= 256 ) {
                digest_.update(buf, 0, 256);
                int newPos = pos - 256;
                System.arraycopy(buf, 256, buf, 0, newPos);
                pos = newPos;
            }
        }

        // final zero
        buf[pos++] = 0;
        digest_.update(buf, 0, pos);
    }

}
