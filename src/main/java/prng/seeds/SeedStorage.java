package prng.seeds;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.SystemRandom;
import prng.generator.IsaacRandom;
import prng.utility.BLOBPrint;
import prng.utility.Config;

/**
 * Storage for PRNG seed information.
 * 
 * @author Simon Greatrix
 *
 */
public abstract class SeedStorage implements AutoCloseable {
    /**
     * Lock for the storage to ensure only a single thread manipulates the
     * storage at a time.
     */
    private static Lock LOCK = new ReentrantLock();

    /** Logger for seed storage operations */
    public static final Logger LOG = LoggerFactory.getLogger(SeedStorage.class);

    /**
     * Set of queued seeds to be written at the next scheduled storage update.
     */
    final static Set<Seed> QUEUE = new HashSet<Seed>();

    /**
     * RNG used by the Scrambler. Initially we use the InstantEntropy source,
     * and then switch to SystemRandom once that is available.
     */
    private static Random RAND = IsaacRandom.getSharedInstance();

    /**
     * Number of milliseconds between storage saves. Takes the value of
     * "savePeriod" from the "config" section and defaults to 5000 milliseconds.
     */
    private static final int SAVE_PERIOD = Math.max(
            Config.getConfig("config", SeedStorage.class).getInt("savePeriod",
                    5000),
            100);

    /**
     * Multiplicative increase in the time between successive saves. For
     * example, if this was set to 2, the time between saves in seconds would be
     * 5, 10, 20, 40, 80 and so on. Takes the value of "savePeriodMultiplier"
     * from the "config" section and default to 1.
     */
    private static final double SAVE_MULTIPLY = Math.max(
            Config.getConfig("config", SeedStorage.class).getDouble(
                    "savePeriodMultiplier", 1),
            1);

    /**
     * Additive increase in the milliseconds between successive saves. For
     * example, if this was set to 5000 the the time between saves in seconds
     * would be 5, 10, 15, 20, 25, 30 and so on. Takes the value of
     * "savePeriodAdd" from the "config" section and defaults to 0.
     */
    private static final int SAVE_ADD = Math.max(
            Config.getConfig("config", SeedStorage.class).getInt(
                    "savePeriodAdd", 0),
            0);

    /**
     * The maximum amount of time between saves. Takes the value of
     * "savePeriodMax" from the "config" section and defaults to 24 hours.
     */
    private static final int SAVE_MAX = Math.max(
            Config.getConfig("config", SeedStorage.class).getInt(
                    "savePeriodMax", 1000 * 60 * 60 * 24),
            1000);

    /** Last time entropy was saved */
    private static long SAVE_DUE = 0;

    /** Last time entropy was saved */
    private static long SAVE_WAIT = 5000;

    static {
        // save any unsaved seeds at shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                synchronized (QUEUE) {
                    if( QUEUE.isEmpty() ) return;

                    // save the enqueued seeds
                    SeedStorage storage = null;
                    try {
                        storage = getInstance();
                    } finally {
                        if( storage != null ) storage.close();
                    }
                }
            }
        });
        SAVE_WAIT = SAVE_PERIOD;
        SAVE_DUE = System.currentTimeMillis() + SAVE_WAIT;
    }


    /**
     * Enqueue a seed update to be written with the next regular update of the
     * seed file
     * 
     * @param seed
     *            the updated seed
     */
    public static void enqueue(Seed seed) {
        LOG.debug("Enqueued {}", seed.getName());
        synchronized (QUEUE) {
            QUEUE.add(seed);
            long now = System.currentTimeMillis();
            if( now < SAVE_DUE ) return;

            SeedStorage storage = null;
            try {
                storage = getInstance();
            } finally {
                if( storage != null ) storage.close();
            }
        }
    }


    /**
     * Get the seed file where seed information can be stored. Only one thread
     * can work with the storage at a time.
     * 
     * @return the seed file instance
     */
    public static SeedStorage getInstance() {
        LOCK.lock();
        Config config = Config.getConfig("config", SeedStorage.class);
        String className = config.get("class",
                UserPrefsStorage.class.getName());
        SeedStorage store = null;
        try {
            Class<?> cl = Class.forName(className);
            Class<? extends SeedStorage> clStore = cl.asSubclass(
                    SeedStorage.class);
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
            LOG.error("Specified class of " + className + " was not accessible",
                    e);
        }
        if( store == null ) store = new UserPrefsStorage();

        synchronized (QUEUE) {
            for(Seed s:QUEUE) {
                store.put(s);
            }
            QUEUE.clear();
        }
        return store;
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
     * Internal method. This method is invoked automatically at the appropriate
     * time. There is no point in invoking it at any other time.
     */
    public static void upgradeScrambler() {
        RAND = SystemRandom.getRandom();
    }


    /**
     * Flush any changes and close the storage. Unlocks the thread lock so that
     * other threads can access the storage.
     */
    @Override
    public void close() {
        synchronized (QUEUE) {
            long saveTime = System.currentTimeMillis();
            if( saveTime >= SAVE_DUE ) {
                // calculate new wait time as a double to avoid overflow
                double saveWait = (SAVE_WAIT * SAVE_MULTIPLY) + SAVE_ADD;
                if( saveWait < SAVE_MAX ) {
                    SAVE_WAIT = (int) saveWait;
                } else {
                    SAVE_WAIT = SAVE_MAX;
                }
            }
            
            // set time for next save
            SAVE_DUE = saveTime + SAVE_WAIT;

            // put all the seed updates into this
            for(Seed s:QUEUE) {
                put(s);
            }
            QUEUE.clear();
        }
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
     *             if the storage cannot be flushed and closed
     */
    protected void closeRaw() throws StorageException {
        // do nothing
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
     * Get raw seed data from persistent storage
     * 
     * @param name
     *            the seed's name
     * @return the raw data
     * @throws StorageException
     *             if the storage cannot be read
     */
    abstract protected byte[] getRaw(String name) throws StorageException;


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
     *             if the storage cannot be written to
     */
    abstract protected void putRaw(String name, byte[] data)
            throws StorageException;


    /**
     * Remove a seed from storage. This is used only when a seed is bad.
     * 
     * @param name
     *            the seed's name
     */
    abstract protected void remove(String name);
}
