package prng;

import java.util.Formatter;

/**
 * Utility functions for printing out binary data in a nice format.
 * 
 * @author Simon Greatrix
 *
 */
public class BLOBPrint {

    /**
     * Dump a binary buffer to a nice text format.
     * 
     * @param buf
     *            the buffer
     * @return nicely formatted text
     */
    public static String toString(byte[] buf) {
        if( buf == null ) return "null";
        return toString(buf, 0, buf.length);
    }


    /**
     * Dump a binary buffer to a nice text format.
     * 
     * @param buf
     *            the buffer
     * @param off
     *            where to start printing. If out of range, starts at beginning
     *            of array.
     * @param len
     *            number of bytes to print. If negative or too large, runs to
     *            end of array.
     * @return nicely formatted text
     */
    @SuppressWarnings("resource")
    public static String toString(byte[] buf, int off, int len) {
        if( buf == null ) return "null";
        if( off < 0 ) off = 0;
        if( (len < 0) || (off + len > buf.length) ) len = buf.length - off;

        StringBuilder buffer = new StringBuilder();
        buffer.append('\n');
        StringBuilder bufBytes = new StringBuilder();
        StringBuilder bufChars = new StringBuilder();

        // Although these are Closable, they are not resource leaks as there is
        // no associated resources
        Formatter fmt = new Formatter(buffer);
        Formatter fmtBytes = new Formatter(bufBytes);
        Formatter fmtChars = new Formatter(bufChars);

        for(int i = 0;i < len;i++) {
            int v = 0xff & buf[i + off];
            fmtBytes.format(" %02x", Integer.valueOf(v));
            fmtChars.format("%c",
                    ((32 <= v) && (v <= 126)) ? Character.valueOf((char) v)
                            : Character.valueOf('.'));
            if( (i % 16) == 7 ) {
                // half way break
                fmtBytes.format("  ");
                fmtChars.format(" ");
            } else if( (i % 16) == 15 ) {
                // end of line
                fmtBytes.flush();
                fmtChars.flush();
                fmt.format("%06x  %-50s  %-17s\n", Integer.valueOf(i - 15),
                        bufBytes.toString(), bufChars.toString());
                bufBytes.setLength(0);
                bufChars.setLength(0);
            }
        }

        // add last line
        if( len % 16 != 0 ) {
            fmtBytes.flush();
            fmtChars.flush();
            fmt.format("%06x  %-50s  %-17s\n",
                    Integer.valueOf(16 * (len / 16)), bufBytes.toString(),
                    bufChars.toString());
        }

        // ensure output ready and return
        fmt.flush();
        return buffer.toString();
    }

}
