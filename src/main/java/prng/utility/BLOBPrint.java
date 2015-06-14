package prng.utility;


/**
 * Utility functions for printing out binary data in a nice format.
 * 
 * @author Simon Greatrix
 *
 */
public class BLOBPrint {

    /** Hexadecimal digits */
    private static final char[] HEX = new char[] { '0', '1', '2', '3', '4',
            '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };


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
    public static String toString(byte[] buf, int off, int len) {
        if( buf == null ) return "null";
        if( off < 0 ) off = 0;
        if( (len < 0) || (off + len > buf.length) ) len = buf.length - off;

        StringBuilder buffer = new StringBuilder();
        buffer.append('\n');
        StringBuilder bufBytes = new StringBuilder();
        StringBuilder bufChars = new StringBuilder();
        for(int i = 0;i < len;i++) {
            int v = 0xff & buf[i + off];
            bufBytes.append(HEX[v >> 4]).append(HEX[v & 0xf]).append(' ');
            bufChars.append(((32 <= v) && (v <= 126)) ? ((char) v) : '.');
            if( (i % 16) == 7 ) {
                // half way break
                bufBytes.append("  ");
                bufChars.append(" ");
            } else if( (i % 16) == 15 ) {
                // end of line
                buffer.append(String.format("%06x  %-50s  %-17s\n",
                        Integer.valueOf(i - 15), bufBytes.toString(),
                        bufChars.toString()));
                bufBytes.setLength(0);
                bufChars.setLength(0);
            }
        }

        // add last line
        if( len % 16 != 0 ) {
            buffer.append(String.format("%06x  %-50s  %-17s\n",
                    Integer.valueOf(16 * (len / 16)), bufBytes.toString(),
                    bufChars.toString()));
        }

        return buffer.toString();
    }
}
