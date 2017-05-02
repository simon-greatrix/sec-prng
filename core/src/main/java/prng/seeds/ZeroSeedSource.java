package prng.seeds;

import prng.generator.SeedSource;

/**
 * All requested seeds consist wholly of zeros.
 * 
 * @author Simon Greatrix
 *
 */
public class ZeroSeedSource implements SeedSource {

    @Override
    public byte[] getSeed(int size) {
        return new byte[size];
    }

}
