package prng.seeds;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import prng.BLOBPrint;

/**
 * Write data to a byte array, prior to saving it.
 * 
 * @author Simon Greatrix
 *
 */
public class SeedOutput implements DataOutput {
    /**
     * The buffer where data is stored.
     */
    protected byte buf_[];

    /**
     * The number of valid bytes in the buffer.
     */
    protected int count_;

    /** Buffer for encoding primitives */
    private byte writeBuffer_[] = new byte[8];


    /**
     * New default sized data output.
     */
    public SeedOutput() {
        this(32);
    }


    /**
     * New specified sized data output.
     * 
     * @param size
     *            the initial size of the data buffer
     */
    public SeedOutput(int size) {
        if( size < 0 ) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        buf_ = new byte[size];
    }


    /**
     * Increases the capacity if necessary to ensure that it can hold at least
     * the number of elements specified by the minimum capacity argument.
     * 
     * @param minCapacity
     *            the desired minimum capacity
     * @throws OutOfMemoryError
     *             if {@code minCapacity < 0}. This is interpreted as a request
     *             for the unsatisfiably large capacity
     *             {@code (long) Integer.MAX_VALUE + (minCapacity - Integer.MAX_VALUE)}
     *             .
     */
    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        if( minCapacity - buf_.length > 0 ) grow(minCapacity);
    }


    /**
     * Increases the capacity to ensure that it can hold at least the number of
     * elements specified by the minimum capacity argument.
     * 
     * @param minCapacity
     *            the desired minimum capacity
     */
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf_.length;
        int newCapacity = oldCapacity << 1;
        if( newCapacity - minCapacity < 0 ) newCapacity = minCapacity;
        if( newCapacity < 0 ) {
            if( minCapacity < 0 ) // overflow
                throw new OutOfMemoryError();
            newCapacity = Integer.MAX_VALUE;
        }
        buf_ = Arrays.copyOf(buf_, newCapacity);
    }


    /**
     * Resets the <code>count</code> field of this byte array output stream to
     * zero, so that all currently accumulated output in the output stream is
     * discarded. The output stream can be used again, reusing the already
     * allocated buffer space.
     * 
     * @see java.io.ByteArrayInputStream#count_
     */
    public void reset() {
        count_ = 0;
    }


    /**
     * Returns the current size of the buffer.
     * 
     * @return the number of valid bytes in this output stream.
     */
    public int size() {
        return count_;
    }


    /**
     * Get the output as a ByteBuffer
     * 
     * @return the ByteBuffer
     */
    public ByteBuffer toBuffer() {
        return ByteBuffer.wrap(buf_, 0, count_);
    }


    /**
     * Creates a newly allocated byte array. Its size is the current size of
     * this output stream and the valid contents of the buffer have been copied
     * into it.
     * 
     * @return the current contents of this output stream, as a byte array.
     */
    public byte toByteArray()[] {
        return Arrays.copyOf(buf_, count_);
    }


    /**
     * The data in this buffer hex encoded.
     * 
     * @return the data
     */
    @Override
    public String toString() {
        return BLOBPrint.toString(toByteArray());
    }


    /**
     * Writes <code>len</code> bytes from the specified byte array starting at
     * offset <code>off</code> to this byte array output stream.
     * 
     * @param b
     *            the data.
     * @param off
     *            the start offset in the data.
     * @param len
     *            the number of bytes to write.
     */
    @Override
    public void write(byte b[], int off, int len) {
        if( (off < 0) || (off > b.length) || (len < 0)
                || ((off + len) - b.length > 0) ) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(count_ + len);
        System.arraycopy(b, off, buf_, count_, len);
        count_ += len;
    }


    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }


    /**
     * Writes the specified byte to this byte array output stream.
     * 
     * @param b
     *            the byte to be written.
     */
    @Override
    public void write(int b) {
        ensureCapacity(count_ + 1);
        buf_[count_] = (byte) b;
        count_ += 1;
    }


    @Override
    public void writeBoolean(boolean v) {
        write(v ? 1 : 0);
    }


    @Override
    public void writeByte(int v) {
        write(v);
    }


    @Override
    public void writeBytes(String s) {
        int len = s.length();
        for(int i = 0;i < len;i++) {
            write((byte) s.charAt(i));
        }
    }


    @Override
    public void writeChar(int v) {
        write((v >>> 8) & 0xFF);
        write((v >>> 0) & 0xFF);
    }


    @Override
    public void writeChars(String s) {
        int len = s.length();
        for(int i = 0;i < len;i++) {
            int v = s.charAt(i);
            write((v >>> 8) & 0xFF);
            write((v >>> 0) & 0xFF);
        }
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
        writeBuffer_[0] = (byte) (v >>> 24);
        writeBuffer_[1] = (byte) (v >>> 16);
        writeBuffer_[2] = (byte) (v >>> 8);
        writeBuffer_[3] = (byte) (v);
        write((v >>> 24) & 0xFF);
        write(writeBuffer_, 0, 4);
    }


    @Override
    public void writeLong(long v) {
        writeBuffer_[0] = (byte) (v >>> 56);
        writeBuffer_[1] = (byte) (v >>> 48);
        writeBuffer_[2] = (byte) (v >>> 40);
        writeBuffer_[3] = (byte) (v >>> 32);
        writeBuffer_[4] = (byte) (v >>> 24);
        writeBuffer_[5] = (byte) (v >>> 16);
        writeBuffer_[6] = (byte) (v >>> 8);
        writeBuffer_[7] = (byte) (v);
        write(writeBuffer_, 0, 8);
    }


    @Override
    public void writeShort(int v) {
        write((v >>> 8) & 0xFF);
        write(v & 0xFF);
    }


    /**
     * Write out seed information. The seed bits will be scrambled.
     * 
     * @param seed
     *            the seed to write out.
     */
    public void writeSeed(byte[] seed) {
        writeShort(seed.length);
        write(SeedStorage.scramble(seed));
    }


    /**
     * Writes the complete contents of this byte array output stream to the
     * specified output stream argument, as if by calling the output stream's
     * write method using <code>out.write(buf, 0, count)</code>.
     * 
     * @param out
     *            the output stream to which to write the data.
     * @exception IOException
     *                if an I/O error occurs.
     */
    public void writeTo(OutputStream out) throws IOException {
        out.write(buf_, 0, count_);
    }


    /**
     * As specified by DataOutput. Instead of throwing a UTFDataFormatException
     * if the String requires over 65535 bytes to encode, an
     * IllegalArgumentException is thrown.
     * 
     * @param str
     *            the string to encode
     */
    @Override
    public void writeUTF(String str) {
        // copied from the DataOutputStream source.
        int strlen = str.length();
        int utflen = 0;
        int c, count = 0;

        for(int i = 0;i < strlen;i++) {
            c = str.charAt(i);
            if( (c >= 0x0001) && (c <= 0x007F) ) {
                utflen++;
            } else if( c > 0x07FF ) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }

        if( utflen > 65535 ) {
            throw new IllegalArgumentException("Encoded string too long: "
                    + utflen + " bytes");
        }

        // create output array
        byte[] bytearr = new byte[utflen + 2];
        bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

        // write chars to byte array
        for(int i = 0;i < strlen;i++) {
            c = str.charAt(i);
            if( (c >= 0x0001) && (c <= 0x007F) ) {
                bytearr[count++] = (byte) c;

            } else if( c > 0x07FF ) {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            } else {
                bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
        }

        write(bytearr, 0, utflen + 2);
    }
}
