package prng.seeds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.SystemRandom;

/**
 * Storage for PRNG seed information
 * 
 * @author Simon Greatrix
 *
 */
public abstract class SeedStorage {
    /** Logger for seed storage operations */
    public static final Logger LOG = LoggerFactory.getLogger(SeedStorage.class);

    /**
     * Get the seed file where seed information can be stored.
     * 
     * @return the seed file instance
     */
    public static SeedStorage getInstance() {
        return null; // TODO
    }


    /**
     * "Encrypt" some data. This preserves the underlying entropy but changes
     * the bit representation of that entropy. By scrambling on every read and
     * write, knowledge of the seed file contents does not reveal the bits
     * actually used in the algorithms.
     * 
     * @param data
     *            Data to scramble
     * @return scrambled data
     */
    public static byte[] scramble(byte[] data) {
        int len = data.length;
        byte[] output = new byte[len];
        byte[] cipher = new byte[len];

        // Have to use SystemRandom as Fortuna may not be seeded yet. In theory,
        // this is a cipher. If we had an exact record of the PRNG's state to
        // use as a key we could decrypt the output at a later time. However, we
        // have no intention of ever allowing anyone to decrypt it as that would
        // reveal the seed data. The fact that it is theoretically possible to
        // decrypt it means that no information has been lost and hence our
        // Shannon entropy is preserved.
        SystemRandom.nextBytes(cipher);
        for(int i = 0;i < len;i++) {
            output[i] = (byte) (data[i] ^ cipher[i]);
        }
        return output;
    }


    /**
     * Save a seed to persistent storage
     * 
     * @param seed
     *            the seed to save
     * @throws StorageException
     */
    public void put(Seed seed) throws StorageException {
        LOG.debug("Putting seed {} into store",seed);
        SeedOutput output = new SeedOutput();
        seed.save(output);
        byte[] data = output.toByteArray();
        putRaw(seed.getName(), data);
    }


    /**
     * Store the raw form of a seed
     * 
     * @param name
     *            the seed's name
     * @param data
     *            the seed's raw data
     * @throws StorageException
     */
    abstract protected void putRaw(String name, byte[] data) throws StorageException;


    /**
     * Get a seed from persistent storage
     * 
     * @param name
     *            the seed's name
     * @return the seed, or null
     * @throws StorageException
     */
    public Seed get(String name) throws StorageException {
        LOG.debug("Fetching seed {} from store",name);
        byte[] data = getRaw(name);
        if( data == null ) {
            LOG.info("Seed data {} not found in storage",name);
            return null;
        }

        SeedInput input = new SeedInput(data);
        Seed seed = new Seed();
        try {
            seed.initialize(input);
        } catch ( Exception e ) {
            LOG.error("Seed data for {} was corrupt",name,e);
            remove(name);            
        }
        return seed;
    }


    /**
     * Get a seed of a specific type from persistent storage
     * 
     * @param type
     *            the seed's type
     * @param name
     *            the seed's name
     * @return the seed or null
     * @throws StorageException
     */
    public <T extends Seed> T get(Class<T> type, String name) throws StorageException {
        LOG.debug("Fetching seed {} from store",name);
        byte[] data = getRaw(name);
        if( data == null ) {
            LOG.info("Seed data {} not found in storage",name);
            return null;
        }

        T seed;
        try {
            seed = type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            // This is a programming error
            throw new Error("Invalid seed type:" + type.getName(), e);
        }
        SeedInput input = new SeedInput(data);
        try {
            seed.initialize(input);
        } catch ( Exception e ) {
            LOG.error("Seed data for {} was corrupt",name,e);
            remove(name);            
        }
        return seed;
    }


    /**
     * Get raw seed data from persistent storage
     * 
     * @param name
     *            the seed's name
     * @return the raw data
     * @throws StorageException
     */
    abstract protected byte[] getRaw(String name) throws StorageException;


    /**
     * Remove a seed from storage
     * 
     * @param name
     *            the seed's name
     */
    abstract protected void remove(String name);
}
