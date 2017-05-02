package prng.seeds;

import prng.SecureRandomProvider;
import prng.generator.SeedSource;

public class PermutingSeedSource implements SeedSource {

    byte[] data = new byte[256];

    private short[] count = new short[256];

    private int state = 0;


    public PermutingSeedSource() {
        int j = 0;
        for(int i = 0;i < 256;i++) {
            data[i] = (byte) j;
            // These numbers are simply the numbers either side of 128, which
            // just happen to produce a linear congruential generator of full
            // period.
            j = ((j * 129) + 127) & 0xff;
            count[i] = 0;
        }
    }


    public void update() {
        // Implementation of Heap's Algorithm
        while( state < 256 ) {
            if( count[state] < state ) {
                if( (state & 1) == 0 ) {
                    byte t = data[0];
                    data[0] = data[state];
                    data[state] = t;
                } else {
                    int i = count[state];
                    byte t = data[state];
                    data[state] = data[i];
                    data[i] = t;
                }

                count[state]++;
                state = 0;

                return;
            }

            count[state] = 0;
            state++;
        }

        // All permutations have now been generated, so just start over.
        state = 0;
        for(int i = 0;i < 256;i++) {
            count[i] = 0;
        }

        // Note it should actually be physically impossible to get here within
        // the lifetime of the universe. At the time of writing the universe is
        // estimated to be approximately 10^26 nanoseconds old, but there are
        // over 10^500 permutations.
        SecureRandomProvider.LOG.info(
                "More than 10^500 permutations calculated. How come the universe still exists?");
    }


    @Override
    public byte[] getSeed(int size) {
        byte[] seed = new byte[size];
        int off = 0;
        while( size > 256 ) {
            System.arraycopy(data, 0, seed, off, 256);
            update();
            off += 256;
            size -= 256;
        }
        System.arraycopy(data, 0, seed, off, size);
        update();
        return seed;
    }

}
