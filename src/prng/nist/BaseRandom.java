package prng.nist;

import java.security.SecureRandomSpi;

import prng.Fortuna;
import prng.NonceFactory;
import prng.SeedSource;

/**
 * Common NIST secure random number functionality.
 * 
 * @author Simon Greatrix
 *
 */
abstract public class BaseRandom extends SecureRandomSpi {
    /** serial version UID */
    private static final long serialVersionUID = 1l;

    /**
     * The re-seed counter
     */
    private int counter_ = 1;

    /**
     * A counter for how often this can generate bytes before needing reseeding.
     */
    private final int resistance_;

    /**
     * Number of bytes required for a re-seed
     */
    private final int seedSize_;

    /** Source of entropy */
    private final SeedSource source_;


    /**
     * New instance.
     * 
     * @param source
     *            source of seed information
     * @param resistance
     *            number of operations between re-seeds
     * @param seedSize
     *            the number of bytes in a re-seed.
     */
    protected BaseRandom(SeedSource source, int resistance, int seedSize) {
        source_ = (source == null) ? Fortuna.SOURCE : source;
        resistance_ = resistance;
        seedSize_ = seedSize;
    }


    /**
     * Concatenate the standard material inputs
     * 
     * @param entropy
     *            the supplied entropy
     * @param nonce
     *            the supplied nonce
     * @param personalization
     *            the supplied personalization
     * @param minEntropy
     *            the minimum bytes of entropy
     * @param desiredEntropy
     *            the desired bytes of entropy if none supplied
     * @return concatenated data
     */
    protected byte[] combineMaterials(byte[] entropy, byte[] nonce,
            byte[] personalization, int minEntropy, int desiredEntropy) {
        if( entropy == null ) entropy = source_.getSeed(desiredEntropy);
        if( entropy.length < minEntropy ) {
            byte[] newEntropy = Fortuna.getSeed(minEntropy);
            System.arraycopy(entropy, 0, newEntropy, 0, entropy.length);
            entropy = newEntropy;
        }
        if( nonce == null ) nonce = NonceFactory.create();
        if( personalization == null )
            personalization = NonceFactory.personalization();

        int inputLength = entropy.length + nonce.length
                + personalization.length;
        byte[] seedMaterial = new byte[inputLength];
        System.arraycopy(entropy, 0, seedMaterial, 0, entropy.length);
        System.arraycopy(nonce, 0, seedMaterial, entropy.length, nonce.length);
        System.arraycopy(personalization, 0, seedMaterial, entropy.length
                + nonce.length, personalization.length);
        return seedMaterial;
    }


    @Override
    protected final byte[] engineGenerateSeed(int size) {
        return source_.getSeed(size);
    }


    @Override
    protected final synchronized void engineNextBytes(byte[] bytes) {
        if( resistance_ < counter_ ) {
            engineSetSeed(engineGenerateSeed(seedSize_));
        }
        implNextBytes(bytes);
        counter_++;
    }


    @Override
    protected final synchronized void engineSetSeed(byte[] seed) {
        implSetSeed(seed);

        // if we re-seed on every operation, do not reset the counter
        if( resistance_ != 0 ) counter_ = 1;
    }


    /**
     * The implementation for generating the next bytes, ignoring reseeds
     * 
     * @param bytes
     *            the bytes to generate
     */
    abstract protected void implNextBytes(byte[] bytes);


    /**
     * The implementation for updating the seed, ignoring reseed tracking
     * 
     * @param seed
     *            the bytes to update with
     */
    abstract protected void implSetSeed(byte[] seed);


    /**
     * Create a value that can be used to seed this algorithm without loss of
     * entropy
     * 
     * @return a value for seeding this algorithm
     */
    protected byte[] newSeed() {
        byte[] seed = new byte[seedSize_];
        engineNextBytes(seed);
        return seed;
    }
}
