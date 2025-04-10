package prng.seeds;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import prng.LoggersFactory;
import prng.SystemRandom;
import prng.config.Config;
import prng.generator.IsaacRandom;
import prng.utility.BLOBPrint;

/**
 * Storage for PRNG seed information.
 *
 * @author Simon Greatrix
 */
public abstract class SeedStorage implements AutoCloseable {

  /** Logger for seed storage operations */
  public static final Logger LOG = LoggersFactory.getLogger(SeedStorage.class);

  /**
   * Lock for the storage to ensure only a single thread manipulates the storage at a time. NEVER lock this whilst holding a sync on QUEUE.
   * The lock is acquired by getting an instance of this and released when close() is called.
   */
  private static final Lock LOCK = new ReentrantLock();

  /**
   * Set of queued seeds to be written at the next scheduled storage update.
   */
  private static final Set<Seed> QUEUE = new HashSet<>();

  /**
   * Additive increase in the milliseconds between successive saves. For example, if this was set to 5000 then the time between saves in seconds would be 5,
   * 10, 15, 20, 25, 30 and so on. Takes the value of "savePeriodAdd" from the "config" section and defaults to 5000.
   */
  private static final int SAVE_ADD = Math.max(Config.getConfig("config", SeedStorage.class).getInt("savePeriodAdd", 5000), 0);

  /**
   * The maximum amount of time between saves. Takes the value of "savePeriodMax" from the "config" section and defaults to 24 hours.
   */
  private static final int SAVE_MAX = Math.max(Config.getConfig("config", SeedStorage.class).getInt("savePeriodMax", 1000 * 60 * 60 * 24), 1000);

  /**
   * Multiplicative increase in the time between successive saves. For example, if this was set to 2, the time between saves in seconds would be 5, 10, 20, 40,
   * 80 and so on. Takes the value of "savePeriodMultiplier" from the "config" section and default to 1.
   */
  private static final double SAVE_MULTIPLY = Math.max(Config.getConfig("config", SeedStorage.class).getDouble("savePeriodMultiplier", 1), 1);

  /**
   * Number of milliseconds between storage saves. Takes the value of "savePeriod" from the "config" section and defaults to 5000 milliseconds.
   */
  private static final int SAVE_PERIOD = Math.max(Config.getConfig("config", SeedStorage.class).getInt("savePeriod", 5000), 100);

  /**
   * RNG used by the Scrambler. Initially we use the InstantEntropy source, and then switch to SystemRandom once that is available.
   */
  private static Random RAND = IsaacRandom.getSharedInstance();

  /** Last time entropy was saved */
  private static long SAVE_DUE;

  /** Last time entropy was saved */
  private static long SAVE_WAIT;


  /**
   * Enqueue a seed update to be written with the next regular update of the seed file
   *
   * @param seed the updated seed
   */
  public static void enqueue(Seed seed) {
    LOG.trace("Enqueued {}", seed.getName());
    synchronized (QUEUE) {
      QUEUE.add(seed);
    }

    // Is a save due?
    long now = System.currentTimeMillis();
    if (now < SAVE_DUE) {
      return;
    }

    // A save is due - just open and close the store to save it.
    try (SeedStorage storage = getInstance()) {
      LOG.debug("Saving entropy seeds");
    }
  }


  /**
   * Get the seed file where seed information can be stored. Only one thread can work with the storage at a time.
   *
   * @return the seed file instance
   */
  public static SeedStorage getInstance() {
    LOCK.lock();
    try {
      Config config = Config.getConfig("config", SeedStorage.class);

      // Get the class name and try to create it
      String className = config.get("class", UserPrefsStorage.class.getName());
      SeedStorage store = null;
      try {
        Class<?> cl = Class.forName(className);
        Class<? extends SeedStorage> clStore = cl.asSubclass(SeedStorage.class);
        store = clStore.getDeclaredConstructor().newInstance();
      } catch (ClassCastException e) {
        // bad specification
        LOG.error("Specified class of {} is not a subclass of {}", className, SeedStorage.class.getName());
      } catch (ClassNotFoundException e) {
        // bad specification and classpath
        LOG.error("Specified class of {} was not found", className);
      } catch (InstantiationException e) {
        // something went wrong
        if (e.getCause() instanceof StorageException) {
          LOG.error(e.getCause().getMessage(), e.getCause());
        }
        LOG.error("Specified class of {} failed to load", className, e);
      } catch (IllegalAccessException e) {
        // unexpected
        LOG.error("Specified class of {} was not accessible", className, e);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getTargetException();
        LOG.error("Creating seed storage of class {} failed", className, cause);
      } catch (NoSuchMethodException e) {
        LOG.error("Specified class of {} lacks a zero argument constructor", className, e);
      }

      // If we didn't create a store, try a fall-back
      if (store == null) {
        try {
          store = new UserPrefsStorage();
        } catch (StorageException se) {
          LOG.error("Failed to use user preferences for seed storage.", se);
          store = new FileStorage();
        }
      }

      // Get all the queued seeds
      Seed[] toFlush;
      synchronized (QUEUE) {
        toFlush = QUEUE.toArray(new Seed[0]);
        QUEUE.clear();
      }

      // put all the seed updates into this
      for (Seed s : toFlush) {
        store.put(s);
      }
      return store;
    } catch (RuntimeException | Error e) {
      LOCK.unlock();
      throw e;
    }
  }


  /**
   * "Encrypt" some data. This preserves the underlying entropy but changes the bit representation of that entropy. By scrambling on every read and write,
   * knowledge of the seed file contents does not reveal the bits actually used in the algorithms.
   *
   * @param data Data to scramble
   *
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
    for (int i = 0; i < len; i++) {
      output[i] = (byte) (data[i] ^ cipher[i]);
    }
    return output;
  }


  /**
   * Internal method. This method is invoked automatically at the appropriate time. There is no point in invoking it at any other time.
   */
  public static void upgradeScrambler() {
    RAND = SystemRandom.getRandom();
  }


  static {
    // save any unsaved seeds at shutdown
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      synchronized (QUEUE) {
        if (QUEUE.isEmpty()) {
          return;
        }
      }

      // save the enqueued seeds
      try (SeedStorage storage = getInstance()) {
        LOG.debug("Saving seed information");
      }
    }));
    SAVE_WAIT = SAVE_PERIOD;
    SAVE_DUE = System.currentTimeMillis() + SAVE_WAIT;
  }


  /**
   * Flush any changes and close the storage. Unlocks the thread lock so that other threads can access the storage.
   */
  @Override
  public void close() {
    try {
      Seed[] toFlush;
      synchronized (QUEUE) {
        long saveTime = System.currentTimeMillis();
        if (saveTime >= SAVE_DUE) {
          // calculate new wait time as a double to avoid overflow
          double saveWait = (SAVE_WAIT * SAVE_MULTIPLY) + SAVE_ADD;
          if (saveWait < SAVE_MAX) {
            SAVE_WAIT = (int) saveWait;
          } else {
            SAVE_WAIT = SAVE_MAX;
          }
        }

        // set time for next save
        SAVE_DUE = saveTime + SAVE_WAIT;

        toFlush = QUEUE.toArray(new Seed[0]);
        QUEUE.clear();
      }

      // put all the seed updates into this
      for (Seed s : toFlush) {
        put(s);
      }

      try {
        closeRaw();
      } catch (StorageException e) {
        LOG.error("Failed to persist seeds to storage", e);
      }
    } finally {
      // always unlock
      LOCK.unlock();
    }
  }


  /**
   * Close the actual storage
   *
   * @throws StorageException if the storage cannot be flushed and closed
   */
  protected void closeRaw() throws StorageException {
    // do nothing
  }


  /**
   * Get a seed of a specific type from persistent storage
   *
   * @param type the seed's type
   * @param name the seed's name
   * @param <T>  type of seed
   *
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
    if (data == null) {
      LOG.info("Seed data {} not found in storage", name);
      return null;
    }

    T seed;
    try {
      seed = type.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      // This is a programming error
      throw new InternalError("Invalid seed type:" + type.getName(), e);
    }
    SeedInput input = new SeedInput(data);
    try {
      seed.initialize(input);
    } catch (Exception e) {
      LOG.error("Seed data for {} was corrupt:\n{}", name, BLOBPrint.toString(data), e);
      remove(name);
    }
    return seed;
  }


  /**
   * Get a seed from persistent storage
   *
   * @param name the seed's name
   *
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
    if (data == null) {
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
          BLOBPrint.toString(data), e
      );
      remove(name);
    }
    return seed;
  }


  /**
   * Get raw seed data from persistent storage
   *
   * @param name the seed's name
   *
   * @return the raw data
   *
   * @throws StorageException if the storage cannot be read
   */
  protected abstract byte[] getRaw(String name) throws StorageException;


  /**
   * Save a seed to persistent storage
   *
   * @param seed the seed to save
   */
  public void put(Seed seed) {
    SeedOutput output = new SeedOutput();
    seed.save(output);
    LOG.debug("Putting seed into store\n{}", seed);
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
   * @param name the seed's name
   * @param data the seed's raw data
   *
   * @throws StorageException if the storage cannot be written to
   */
  protected abstract void putRaw(String name, byte[] data) throws StorageException;


  /**
   * Remove a seed from storage. This is used only when a seed is bad.
   *
   * @param name the seed's name
   */
  protected abstract void remove(String name);

}
