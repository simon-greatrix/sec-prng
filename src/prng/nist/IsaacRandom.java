package prng.nist;

import java.util.Arrays;
import java.util.Random;

/**
 * The ISAAC random number generator is a cryptographically secure generator
 * inspired by the RC4 cipher.
 * 
 * @see http://en.wikipedia.org/wiki/ISAAC_%28cipher%29
 *
 */
public class IsaacRandom extends Random {
    /** serial version UID */
    private static final long serialVersionUID = 1L;

    /** Output of last generation */
    private final int[] randResult_ = new int[256];

    /** The number of values used from the current result */
    private int valuesUsed_;

    /** Internal generator state */
    private final int[] mm_ = new int[256];

    /** Internal generator state */
    private int aa_ = 0, bb_ = 0, cc_ = 0;


    /** Create a unseeded generator */
    public IsaacRandom() {
        super(0);
        init(null);
    }


    /**
     * Create a generator with the specified seed
     * 
     * @param seed
     *            the full seed
     */
    public IsaacRandom(int[] seed) {
        super(0);
        setSeed(seed);
    }


    /**
     * Create a generator using each character from the seed as one integer in
     * the seed.
     * 
     * @param seed
     *            the seed
     */
    public IsaacRandom(String seed) {
        super(0);
        setSeed(seed);
    }


    private void generateMoreResults() {
        cc_++;
        bb_ += cc_;

        for(int i = 0;i < 256;i++) {
            int x = mm_[i];
            switch (i & 3) {
            case 0:
                aa_ = aa_ ^ (aa_ << 13);
                break;
            case 1:
                aa_ = aa_ ^ (aa_ >>> 6);
                break;
            case 2:
                aa_ = aa_ ^ (aa_ << 2);
                break;
            case 3:
                aa_ = aa_ ^ (aa_ >>> 16);
                break;
            }
            aa_ = mm_[i ^ 128] + aa_;
            int y = mm_[i] = mm_[(x >>> 2) & 0xFF] + aa_ + bb_;
            randResult_[i] = bb_ = mm_[(y >>> 10) & 0xFF] + x;
        }

        valuesUsed_ = 0;
    }


    /**
     * Mix the bits of 8 integers
     * 
     * @param s
     *            the 8 integers to mix
     */
    private static void mix(int[] s) {
        s[0] ^= s[1] << 11;
        s[3] += s[0];
        s[1] += s[2];
        s[1] ^= s[2] >>> 2;
        s[4] += s[1];
        s[2] += s[3];
        s[2] ^= s[3] << 8;
        s[5] += s[2];
        s[3] += s[4];
        s[3] ^= s[4] >>> 16;
        s[6] += s[3];
        s[4] += s[5];
        s[4] ^= s[5] << 10;
        s[7] += s[4];
        s[5] += s[6];
        s[5] ^= s[6] >>> 4;
        s[0] += s[5];
        s[6] += s[7];
        s[6] ^= s[7] << 8;
        s[1] += s[6];
        s[7] += s[0];
        s[7] ^= s[0] >>> 9;
        s[2] += s[7];
        s[0] += s[1];
    }


    private void init(int[] seed) {
        if( seed != null && seed.length != 256 ) {
            seed = Arrays.copyOf(seed, 256);
        }
        int[] initState = new int[8];
        // initialise to the golden ratio
        Arrays.fill(initState, 0x9e3779b9);

        for(int i = 0;i < 4;i++) {
            mix(initState);
        }

        for(int i = 0;i < 256;i += 8) {
            if( seed != null ) {
                for(int j = 0;j < 8;j++) {
                    initState[j] += seed[i + j];
                }
            }
            mix(initState);
            for(int j = 0;j < 8;j++) {
                mm_[i + j] = initState[j];
            }
        }

        if( seed != null ) {
            for(int i = 0;i < 256;i += 8) {
                for(int j = 0;j < 8;j++) {
                    initState[j] += mm_[i + j];
                }

                mix(initState);

                for(int j = 0;j < 8;j++) {
                    mm_[i + j] = initState[j];
                }
            }
        }
        
        // Make sure generateMoreResults() will be called by
        // the next next() call.
        valuesUsed_ = 256;
    }


    @Override
    protected int next(int bits) {
        synchronized (randResult_) {
            if( valuesUsed_ == 256 ) {
                generateMoreResults();
                assert (valuesUsed_ == 0);
            }
            int value = randResult_[valuesUsed_];
            valuesUsed_++;
            return value >>> (32 - bits);
        }
    }


    @Override
    public void setSeed(long seed) {
        if( mm_ == null ) {
            // We're being called from the superclass constructor. We don't
            // have our state arrays instantiated yet, and we're going to do
            // proper initialization later in our own constructor anyway, so
            // just ignore this call.
            return;
        }

        synchronized (randResult_) {
            super.setSeed(0);
            int[] arraySeed = new int[256];
            arraySeed[0] = (int) (seed & 0xFFFFFFFF);
            arraySeed[1] = (int) (seed >>> 32);
            init(arraySeed);
        }
    }


    public void setSeed(int[] seed) {
        synchronized (randResult_) {
            super.setSeed(0);
            init(seed);
        }
    }


    public void setSeed(String seed) {
        synchronized (randResult_) {
            super.setSeed(0);
            char[] charSeed = seed.toCharArray();
            int[] intSeed = new int[charSeed.length];
            for(int i = 0;i < charSeed.length;i++) {
                intSeed[i] = charSeed[i];
            }
            init(intSeed);
        }
    }

}