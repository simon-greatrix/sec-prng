package prng.generator;

import java.security.SecureRandomSpi;

import prng.Fortuna;
import prng.utility.NonceFactory;

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
     * Counter-intuitively, higher values of resistance are less secure.
     */
    private final int resistance_;

    /**
     * Number of bytes required for a re-seed
     */
    private final int seedSize_;

    /** Source of entropy */
    private final SeedSource source_;

    /** Number of spare bytes currently available */
    private int spareBytes_ = 0;

    /** Storage for spare bytes */
    private final byte[] spares_;


    /**
     * New instance.
     * 
     * @param source
     *            source of seed information
     * @param resistance
     *            number of operations between re-seeds
     * @param seedSize
     *            the number of bytes in a re-seed.
     * @param spareSize
     *            the maximum space required for spare bytes
     */
    protected BaseRandom(SeedSource source, int resistance, int seedSize,
            int spareSize) {
        source_ = (source == null) ? Fortuna.SOURCE : source;
        resistance_ = resistance;
        seedSize_ = seedSize;
        spares_ = new byte[spareSize];
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
        System.arraycopy(personalization, 0, seedMaterial,
                entropy.length + nonce.length, personalization.length);
        return seedMaterial;
    }


    @Override
    protected final byte[] engineGenerateSeed(int size) {
        return source_.getSeed(size);
    }


    @Override
    protected final synchronized void engineNextBytes(byte[] bytes) {
        int offset = 0;
        if( spareBytes_ > 0 ) {
            int toUse = Math.min(spareBytes_, bytes.length);
            System.arraycopy(spares_, 0, bytes, 0, toUse);
            spareBytes_ -= toUse;
            offset += toUse;
            if( offset == bytes.length ) {
                return;
            }
        }
        if( resistance_ < counter_ ) {
            engineSetSeed(engineGenerateSeed(seedSize_));
        } else {
            counter_++;
        }
        implNextBytes(offset, bytes);
    }


    @Override
    protected final synchronized void engineSetSeed(byte[] seed) {
        implSetSeed(seed);

        // reset the counter
        counter_ = 1;
    }


    /**
     * The implementation for generating the next bytes, ignoring reseeds
     * 
     * @param offset
     *            first byte to start
     * @param bytes
     *            the bytes to generate
     */
    abstract protected void implNextBytes(int offset, byte[] bytes);


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
    public byte[] newSeed() {
        byte[] seed = new byte[seedSize_];
        engineNextBytes(seed);
        return seed;
    }


    /**
     * Set the spare bytes available for the next round
     * 
     * @param data
     *            the spares
     * @param offset
     *            the offset into the input
     * @param length
     *            the number of bytes
     */
    protected void setSpares(byte[] data, int offset, int length) {
        spareBytes_ = length;
        System.arraycopy(data, offset, spares_, 0, length);
    }
}
