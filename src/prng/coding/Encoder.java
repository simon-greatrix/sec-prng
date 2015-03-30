package prng.coding;

import java.io.IOException;

public class Encoder {

    /** Number of bits of state. Must be small enough to prevent overflow. */
    protected static final long STATE_SIZE = 32;

    /** Mask of all 1s */
    protected static final long MASK = (1L << STATE_SIZE) - 1;

    /** Mask with just the top bit set. i.e. 1000...0000 */
    protected static final long TOP_MASK = (1L << (STATE_SIZE - 1));

    /** Mask with just the second bit set. i.e. 0100...000 */
    protected static final long SECOND_MASK = (1L << (STATE_SIZE - 2));

    /** The upper limit (inclusive) for the range. */
    protected static final long MAX_RANGE = 1L << STATE_SIZE;

    /** The lower limit (inclusive) for the range */
    protected static final long MIN_RANGE = (1L << (STATE_SIZE - 2)) + 2;

    /** The maximum for the total cumulative frequency */
    protected static final long MAX_TOTAL = Math.min(Long.MAX_VALUE / MAX_RANGE,
            MIN_RANGE);

    /** Low end of encoder's current range */
    protected long low_ = 0;

    /** High end of encoder's current range */
    protected long high_ = MASK;

    /** Observed frequencies */
    protected FrequencyTable freq_ = new FrequencyTable();

    /**
     * Updates the range as a result of seeing the given symbol - i.e. update
     * low and high.
     * 
     * Invariants that are true before and after encoding/decoding each symbol:
     * 
     * <ul>
     * <li><code>0 <= low <= code <= high < 2^STATE_SIZE.</code><br>
     * ('code' exists only in a decoder)<br>
     * Therefore these variables are unsigned integers of STATE_SIZE bits.
     * <li><code>low < 1/2 * 2^STATE_SIZE <= high.</code><br>
     * In other words, they are in different halves of the full range.
     * <li><code>low < 1/4 * 2^STATE_SIZE || high >= 3/4 * 2^STATE_SIZE</code>.<br>
     * In other words, they are not both in the middle two quarters.
     * </ul>
     * 
     * Let <code>range = high - low + 1</code>, then:
     * 
     * <ul>
     * <li><code>MIN_RANGE <= range <= MAX_RANGE = 2^STATE_SIZE.</code>
     * <li>and <code>range > MAX_RANGE/4</code>
     * </ul>
     * 
     * The invariants for <code>range</code> essentially dictate the maximum
     * total that the incoming frequency table can have, such that intermediate
     * calculations don't overflow.
     * 
     * @param symbol
     *            the symbol that was seen.
     */
    protected void update(int symbol) throws IOException {
        // State check
        if( low_ >= high_ || (low_ & MASK) != low_ || (high_ & MASK) != high_ )
            throw new AssertionError("Low or high out of range");
        long range = high_ - low_ + 1;
        if( range < MIN_RANGE || range > MAX_RANGE )
            throw new AssertionError("Range out of range");

        // Frequency table values check
        long total = freq_.getTotal();
        int[] freq = freq_.getLowAndHigh(symbol);
        freq_.increment(symbol);
        long symLow = freq[0];
        long symHigh = freq[1];
        if( symLow == symHigh )
            throw new IllegalArgumentException("Symbol has zero frequency");
        if( total > MAX_TOTAL )
            throw new IllegalArgumentException(
                    "Cannot code symbol because total is too large");

        // Update range
        long newLow = low_ + symLow * range / total;
        long newHigh = low_ + symHigh * range / total - 1;
        low_ = newLow;
        high_ = newHigh;

        // While the highest bits are equal
        while( ((low_ ^ high_) & TOP_MASK) == 0 ) {
            shift();
            low_ = (low_ << 1) & MASK;
            high_ = ((high_ << 1) & MASK) | 1;
        }

        // While the second highest bit of low is 1 and the second highest bit
        // of high is 0
        while( (low_ & ~high_ & SECOND_MASK) != 0 ) {
            underflow();
            low_ = (low_ << 1) & (MASK >>> 1);
            high_ = ((high_ << 1) & (MASK >>> 1)) | TOP_MASK | 1;
        }
    }

    /** Bit-by-bit output */
    private BitOutputStream output;

    /** Number of saved underflow bits. This value can grow without bound. */
    private int underflow;


    /** Creates an arithmetic coding encoder.
     * 
     * @param out
     */
    public Encoder(BitOutputStream out) {
        super();
        if( out == null ) throw new NullPointerException();
        output = out;
        underflow = 0;
    }


    // Encodes a symbol.
    public void write(int symbol) throws IOException {
        update(symbol);
    }


    // Must be called at the end of the stream of input symbols, otherwise the
    // output data cannot be decoded properly.
    public void finish() throws IOException {
        output.write(1);
    }


    protected void shift() throws IOException {
        int bit = (int) (low_ >>> (STATE_SIZE - 1));
        output.write(bit);

        // Write out saved underflow bits
        for(;underflow > 0;underflow--)
            output.write(bit ^ 1);
    }


    protected void underflow() throws IOException {
        underflow++;
        System.out.println(underflow);
        // The maximum the number of bits we pass to Fortuna is 2048. If underflow exceeds it we just discard it.
        if( underflow>=2048 ) underflow=0;
    }
}
