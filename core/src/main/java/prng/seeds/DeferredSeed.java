package prng.seeds;

import java.util.concurrent.Callable;

/**
 * A seed whose data is only derived when it is actually needed.
 * 
 * @author Simon Greatrix
 *
 */
public class DeferredSeed extends Seed {

    /** Source of the seed for when it is needed */
    private final Callable<byte[]> source;


    /**
     * Create a deferred seed
     * 
     * @param name
     *            the name of the seed
     * @param source
     *            the source of the seed data
     */
    public DeferredSeed(String name, Callable<byte[]> source) {
        super(name,null);
        this.source = source;
    }


    /**
     * Initialise this seed with actual data
     */
    private void init() {
        if( data != null ) return;
        try {
            data = source.call();
        } catch (Exception e) {
            SeedStorage.LOG.error("Failed to create seed \"{}\" from {}", name,
                    source.getClass().getName(), e);
            data = new byte[0];
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void save(SeedOutput output) {
        init();
        super.save(output);
    }


    /**
     * Derive the seed and return a copy of it
     */
    @Override
    public byte[] getSeed() {
        init();
        return super.getSeed();
    }


    /**
     * This should never be saved, so it cannot be initialized from input. This
     * method throws an UnsupportedOperationException.
     * 
     * @param input
     *            ignored
     */
    @Override
    public void initialize(SeedInput input) throws Exception {
        throw new UnsupportedOperationException();
    }

}
