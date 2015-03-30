package prng.coding;

/**
 * A mutable table of symbol frequencies. The number of symbols cannot be
 * changed after construction. The current algorithm for calculating cumulative
 * frequencies takes linear time, but there exist faster algorithms.
 */
public final class FrequencyTable {
    /** The cumulative frequency up to each symbol */
    private final int[] cumulative_ = new int[257];

    /** The sum total of all the frequencies */
    private int total_;


    /**
     * Creates a frequency table of the specified size. All frequencies are set
     * to 1.
     * 
     */
    public FrequencyTable() {
        total_ = 0;
        for(int i = 0;i < 256;i++) {
            increment(i);
        }
    }


    /**
     * Increments the frequency of the specified symbol. An exception should be
     * thrown if the symbol is out of range.
     * 
     * @param symbol
     *            the symbol whose frequency to increment
     * @throws IllegalArgumentException
     *             if {@code symbol} &lt; 0 or {@code symbol} &ge;
     *             {@code getSymbolLimit()}
     */
    public void increment(int symbol) {
        symbol++;
        total_ = total_ + 1;
        if( total_ >= Encoder.MAX_TOTAL ) {
            rescale(0.9);
        }
        while( symbol <= 256 ) {
            cumulative_[symbol]++;
            symbol += (symbol & -symbol);
        }
    }


    /**
     * Rescale the frequency table to prevent overflow.
     * 
     * @param scale
     *            the reduction factor
     */
    protected void rescale(double scale) {
        int[] freq = new int[256];
        int newTotal = 0;

        // convert cumulative frequency values into absolute frequencies
        int prev = 0;
        for(int i = 0;i < 256;i++) {
            int cf = get(i);
            int f = cf - prev;
            int newF = 1 + (int) (0.5 + ((f - 1) * scale));
            freq[i] = newF;
            newTotal += newF;
            prev = cf;
        }

        // clear out existing table
        for(int i = 0;i < 257;i++) {
            cumulative_[i] = 0;
        }

        // add rescaled frequencies
        int[] newCuml = cumulative_;
        for(int i = 0;i < 256;i++) {
            int f = freq[i];
            int s = i + 1;
            while( s <= 256 ) {
                newCuml[s] += f;
                s += (s & -s);
            }
        }
        total_ = newTotal;
    }


    /**
     * Returns the total of all symbol frequencies. The returned value is at
     * least 0 and is always equal to {@code getHigh(getSymbolLimit() - 1)}.
     * 
     * @return the total of all symbol frequencies
     */
    public int getTotal() {
        return total_;
    }


    /**
     * Get the cumulative frequency for a symbol
     * 
     * @param symbol
     *            the symbol
     * @return the cumulative frequency
     */
    public int get(int symbol) {
        symbol++;
        int sum = 0;
        while( symbol > 0 ) {
            sum += cumulative_[symbol];
            symbol -= (symbol & -symbol);
        }
        return sum;
    }


    /**
     * Get the low and high cumulative frequencies for a symbol
     * 
     * @param symbol
     *            the symbol
     * @return the low and high values
     */
    public int[] getLowAndHigh(int symbol) {
        int sl = symbol;
        int sh = sl + 1;
        int sc = sl & sh;

        int sumLow = 0, sumHigh = 0, sumCommon = 0;
        while( sl > sc ) {
            sumLow += cumulative_[sl];
            sl -= (sl & -sl);
        }
        while( sh > sc ) {
            sumHigh += cumulative_[sh];
            sh -= (sh & -sh);
        }
        while( sc > 0 ) {
            sumCommon += cumulative_[sc];
            sc -= (sc & -sc);
        }
        return new int[] { sumLow + sumCommon, sumHigh + sumCommon };
    }


    /**
     * Returns a string representation of this frequency table. The current
     * format shows all the symbols and frequencies. The format is subject to
     * change.
     * 
     * @return a string representation of this frequency table
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int f = 0;
        for(int i = 0;i < 256;i++) {
            int f2 = get(i);
            sb.append(String.format("%d\t%d\n", i, f2 - f));
            f = f2;
        }
        return sb.toString();
    }

}