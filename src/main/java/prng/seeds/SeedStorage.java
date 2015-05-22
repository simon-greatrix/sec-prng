package prng.seeds;

import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.BLOBPrint;
import prng.Config;
import prng.SystemRandom;
import prng.nist.IsaacRandom;

/**
 * Storage for PRNG seed information.
 * 
 * @author Simon Greatrix
 *
 */
public abstract class SeedStorage implements AutoCloseable {
    /** Logger for seed storage operations */
    public static final Logger LOG = LoggerFactory.getLogger(SeedStorage.class);

    /**
     * RNG used by the Scrambler. Initially we use the InstantEntropy source,
     * and then switch to SystemRandom once that is available.
     */
    private static Random RAND = IsaacRandom.getSharedInstance();

    /**
     * Lock for the storage to ensure only a single thread manipulates the
     * storage at a time.
     */
    private static Lock LOCK = new ReentrantLock();


    /**
     * Get the seed file where seed information can be stored. Only one thread
     * can work with the storage at a time.
     * 
     * @return the seed file instance
     */
    public static SeedStorage getInstance() {
        LOCK.lock();
        Config config = Config.getConfig("config", SeedStorage.class);
        String className = config.get("class", UserPrefsStorage.class.getName());
        SeedStorage store = null;
        try {
            Class<?> cl = Class.forName(className);
            Class<? extends SeedStorage> clStore = cl.asSubclass(SeedStorage.class);
            store = clStore.newInstance();
        } catch (ClassCastException e) {
            // bad specification
            LOG.error("Specified class of " + className
                    + " is not a subclass of " + SeedStorage.class.getName());
        } catch (ClassNotFoundException e) {
            // bad specification and classpath
            LOG.error("Specified class of " + className + " was not found");
        } catch (InstantiationException e) {
            // something went wrong
            LOG.error("Specified class of " + className + " failed to load", e);
        } catch (IllegalAccessException e) {
            // unexpected
            LOG.error(
                    "Specified class of " + className + " was not accessible",
                    e);
        }
        if( store == null ) store = new UserPrefsStorage();
        return store;
    }


    /**
     * Internal method. Do not invoke this method.
     */
    public static void upgradeScrambler() {
        RAND = SystemRandom.getRandom();
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

        // In theory, this is a cipher. If we had an exact record of the PRNG's
        // state to use as a key we could decrypt the output at a later time.
        // However, we have no intention of ever allowing anyone to decrypt it
        // as that would reveal the seed data. The fact that it is theoretically
        // possible to decrypt it means that no information has been lost and
        // hence our Shannon entropy is preserved.
        RAND.nextBytes(cipher);
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
     */
    public void put(Seed seed) {
        LOG.debug("Putting seed into store\n{}", seed);
        SeedOutput output = new SeedOutput();
        seed.save(output);
        byte[] data = output.toByteArray();
        try {
            putRaw(seed.getName(), data);
        } catch (StorageException se) {
            LOG.error("Failed to store seed\n{}.", seed, se);
        }
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
     */
    public Seed get(String name) {
        LOG.debug("Fetching seed {} from store", name);
        byte[] data;
        try {
            data = getRaw(name);
        } catch (StorageException e1) {
            LOG.error("Could not retrieve seed {}", name, e1);
            return null;
        }
        if( data == null ) {
            LOG.info("Seed data {} not found in storage", name);
            return null;
        }

        SeedInput input = new SeedInput(data);
        Seed seed = new Seed();
        try {
            seed.initialize(input);
        } catch (Exception e) {
            // log the details of the problem
            LOG.error("Seed data for {} was corrupt:\n{}", name,
                    BLOBPrint.toString(data), e);
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
     * @param <T>
     *            type of seed
     * @return the seed or null
     */
    public <T extends Seed> T get(Class<T> type, String name) {
        LOG.debug("Fetching seed {} from store", name);
        byte[] data;
        try {
            data = getRaw(name);
        } catch (StorageException e1) {
            LOG.error("Could not retrieve seed {}", name, e1);
            return null;
        }
        if( data == null ) {
            LOG.info("Seed data {} not found in storage", name);
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
        } catch (Exception e) {
            LOG.error("Seed data for {} was corrupt:\n", name,
                    BLOBPrint.toString(data), e);
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
     * Remove a seed from storage. This is used only when a seed is bad.
     * 
     * @param name
     *            the seed's name
     */
    abstract protected void remove(String name);


    /**
     * Flush any changes and close the storage. Unlocks the thread lock so that
     * other threads can access the storage.
     */
    public void close() {
        try {
            closeRaw();
        } catch (StorageException e) {
            LOG.error("Failed to persist seeds to storage", e);
        } finally {
            LOCK.unlock();
        }
    }


    /**
     * Close the actual storage
     * 
     * @throws StorageException
     */
    protected void closeRaw() throws StorageException {
        // do nothing
    }
}
