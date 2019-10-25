package prng;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import prng.collector.DaemonThreadFactory;
import prng.collector.InstantEntropy;
import prng.generator.HashSpec;
import prng.generator.NistHashRandom;
import prng.generator.SeedSource;
import prng.seeds.SeedStorage;
import prng.utility.DigestDataOutput;
import prng.utility.NonceFactory;

/**
 * <p>System provided secure random sources. We assume there is at least one such source. The sources are multiplexed, with one byte taken from each source in
 * turn. This means that if any source has a good entropic seed, its entropy will be included in all outputs. </p>
 *
 * <p>The sources are periodically cross-pollinated with entropy from each other. </p>
 *
 * <p>Note that since some system provided random sources will block whilst they wait for entropy to arrive (e.g. reading from /dev/random), this class may
 * delay start-up. </p>
 *
 * @author Simon Greatrix
 */
public class SystemRandom implements Runnable {

  /** Logger for this class */
  static final Logger LOG = LoggersFactory.getLogger(SystemRandom.class);

  /**
   * Fetching seed data may block. To prevent waits on re-seeding we use this completion service.
   */
  static final ExecutorCompletionService<Seed> SEED_MAKER;

  /** Block length that is fetched from each source at one time */
  private static final int BLOCK_LEN = 256;

  /**
   * Thread pool executor
   */
  private static final Executor EXECUTOR;

  /** Queue for injected seeds */
  private static final LinkedBlockingQueue<byte[]> INJECTED = new LinkedBlockingQueue<>(
      100);

  /**
   * "Random" selection for which source gets reseeded. The intention is to all sources of seed data to influence all other sources by "randomly" assigning seed
   * data to a source.
   */
  private static final Random RESEED = new Random();

  /** System provided secure random number generators */
  private final static SystemRandom[] SOURCES;

  /** Number of sources */
  private static final int SOURCE_LEN;

  /**
   * Source for getting entropy from the system
   */
  public static final SeedSource SOURCE = SystemRandom::getSeed;

  /**
   * Random number generator that draws from the System random number sources
   */
  private static SecureRandom RANDOM = null;



  /**
   * A seed from one of the system sources
   *
   * @author Simon Greatrix
   */
  static class Seed implements Callable<Seed> {

    /** Secure random seed source */
    final SecureRandom source;

    /** Generated or injected seed */
    byte[] seed = null;


    /**
     * Inject seed data
     *
     * @param seed data to inject
     */
    Seed(byte[] seed) {
      source = null;
      this.seed = seed.clone();
    }


    /**
     * Create seeds from the supplied PRNG
     *
     * @param random seed source
     */
    Seed(SecureRandom random) {
      source = random;
    }


    @Override
    public Seed call() {
      if (source == null) {
        return this;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Generating seed from "
            + source.getProvider().getName() + ":"
            + source.getAlgorithm());
      }
      seed = source.generateSeed(32);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Finished generating seed from "
            + source.getProvider().getName() + ":"
            + source.getAlgorithm());
      }
      return this;
    }


    /**
     * Resubmit this seed source
     */
    public void resubmit() {
      seed = null;
      if (source != null) {
        SEED_MAKER.submit(this);
      }
    }
  }


  /**
   * Get a SecureRandom instance that draws upon the system secure PRNGs for seed entropy. The SecureRandom is based upon a NIST algorithm and will reseed
   * itself with additional entropy after every operation.
   *
   * @return a SecureRandom instance.
   */
  public static SecureRandom getRandom() {
    SecureRandom rand = RANDOM;
    if (rand == null) {
      synchronized (SecureRandom.class) {
        rand = RANDOM;
        if (rand == null) {
          byte[] entropy = getSeed(HashSpec.SPEC_SHA512.seedLength);
          rand = new SecureRandomImpl(new NistHashRandom(SOURCE,
              HashSpec.SPEC_SHA512, 0, entropy, new byte[0], NonceFactory.personalization()
          ));
          RANDOM = rand;
        }
      }
    }
    return rand;
  }


  /**
   * Get seed data from the system secure random number generators. This data is drawn from the output of the system secure random number generators, not their
   * actual entropy sources.
   *
   * @param size number of seed bytes required
   *
   * @return the seed data
   */
  public static byte[] getSeed(int size) {
    byte[] data = new byte[size];
    int index = RESEED.nextInt(SOURCE_LEN);
    for (int i = 0; i < size; i++) {
      // try for a byte
      boolean needByte = true;
      for (int j = 0; needByte && (j < SOURCE_LEN); j++) {
        if (SOURCES[index].get(data, i)) {
          needByte = false;
        }
        index = (index + 1) % SOURCE_LEN;
      }

      // if no byte, get one from instant entropy
      if (needByte) {
        byte[] b = InstantEntropy.SOURCE.getSeed(1);
        data[i] = b[0];
      }
    }
    return data;
  }


  /**
   * Inject seed data into the system random number generators.
   *
   * @param seed data to inject
   */
  public static void injectSeed(byte[] seed) {
    if (seed == null || seed.length == 0) {
      return;
    }

    // Offer it the injection queue.
    while (!INJECTED.offer(seed)) {
      // did not go to queue, so combine some entries to make space
      DigestDataOutput out = new DigestDataOutput("SHA-512");
      out.writeInt(seed.length);
      out.write(seed);

      // attempt to remove 5 entries
      for (int i = 0; i < 5; i++) {
        byte[] s = INJECTED.poll();
        if (s != null) {
          out.write(i);
          out.writeInt(s.length);
          out.write(s);
        }
      }

      // combine entries
      seed = out.digest();
    }
  }


  /**
   * Get random data from the combined system random number generators
   *
   * @param rand byte array to fill
   */
  public static void nextBytes(byte[] rand) {
    getRandom().nextBytes(rand);
  }


  static {
    Provider[] provs = Security.getProviders();
    ArrayList<Service> servs = new ArrayList<>();
    for (Provider prov : provs) {
      // do not loop into our own provider
      if (prov instanceof SecureRandomProvider) {
        continue;
      }
      if (prov.getName().equals("SecureRandomProvider")) {
        continue;
      }

      // check for offered secure random sources
      Set<Service> serv = prov.getServices();
      for (Service s : serv) {
        if (s.getType().equals("SecureRandom")) {
          servs.add(s);
        }
      }
    }

    // Now we know how many services we have, initialise arrays
    int len = servs.size();
    SOURCE_LEN = len;
    SOURCES = new SystemRandom[len];
    ThreadPoolExecutor threadPool = new ThreadPoolExecutor(2 * len, 2 * len, 10, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(), new DaemonThreadFactory("PRNG-SystemRandom")
    );
    threadPool.allowCoreThreadTimeOut(true);
    EXECUTOR = threadPool;
    SEED_MAKER = new ExecutorCompletionService<>(EXECUTOR);

    // create the PRNGs
    for (int i = 0; i < len; i++) {
      Service s = servs.get(i);
      LOG.debug("Found system PRNG " + s.getProvider().getName() + ":"
          + s.getAlgorithm());
      SOURCES[i] = new SystemRandom(s.getProvider(), s.getAlgorithm());
    }
  }

  /** Random bytes drawn from the system PRNG */
  private final byte[] block = new byte[BLOCK_LEN];

  /**
   * Number of bytes available in the current block. A value of -1 means not initialised.
   */
  private int available = -1;

  /** Can this PRNG accept new seed information? (Not all of them can.) */
  private boolean canSeed = true;

  /** The System SecureRandom instance */
  private SecureRandom random = null;

  /** Number of operations before requesting a reseed */
  private int reseed;


  /**
   * Create a new instance using the specified provider and algorithm. If the provider is null, the system "strong" algorithm will be requested. Initialisation
   * will be undertaken asynchronously to prevent blocking.
   *
   * @param prov the provider
   * @param alg  the algorithm
   */
  SystemRandom(final Provider prov, final String alg) {
    EXECUTOR.execute(() -> SystemRandom.this.init(prov, alg));
  }


  private void doReseed(byte[] s) {
    if (canSeed) {
      try {
        random.setSeed(s);
      } catch (ProviderException pe) {
        canSeed = false;
        LOG.debug("PRNG " + random.getProvider().getName() + ":" + random.getAlgorithm()
            + " refused new seed information.");
      } catch (RuntimeException re) {
        canSeed = false;
        LOG.warn("PRNG " + random.getProvider().getName() + ":" + random.getAlgorithm()
            + " failed to accept new seed information.");
      }
    }

    // Don't waste the seed
    if (!canSeed) {
      injectSeed(s);
    }
  }


  /**
   * Fetch new bytes
   */
  private void fetchBytes() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Generating bytes from "
          + random.getProvider().getName() + ":"
          + random.getAlgorithm());
    }

    // Generate random bytes. This may block.
    random.nextBytes(block);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Finished generating bytes from "
          + random.getProvider().getName() + ":"
          + random.getAlgorithm());
    }

    synchronized (this) {
      // update the state
      available = BLOCK_LEN;
    }
  }


  /**
   * Get one byte from this generator, if possible
   *
   * @param output the output array
   * @param pos    where to put the byte
   *
   * @return true if a byte could be supplied
   */
  boolean get(byte[] output, int pos) {
    synchronized (this) {
      // is this initialised?
      if (available == -1) {
        return false;
      }

      // if no bytes available, cannot supply any
      if (available == 0) {
        return false;
      }

      // get a byte
      int p = (--available);
      output[pos] = block[p];

      // have we used all available bytes?
      if (p == 0) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Used all bytes from "
              + random.getProvider().getName() + ":"
              + random.getAlgorithm());
        }

        // asynchronously generate more bytes
        EXECUTOR.execute(this);
      }
    }
    return true;
  }


  /**
   * Asynchronously initialise this.
   *
   * @param prov the provider
   * @param alg  the algorithm
   */
  void init(Provider prov, String alg) {
    // get the specific instance
    LOG.info("Initialising System PRNG: {}:{}", prov.getName(), alg);
    try {
      random = SecureRandom.getInstance(alg, prov);
    } catch (NoSuchAlgorithmException e) {
      // Instance not available.
      LOG.error("Provider " + prov + " does not implement " + alg
          + " after announcing it as a service");
      random = null;
      return;
    }

    // Load the first block. This may block.
    random.nextBytes(block);

    // set when this reseeds
    reseed = RESEED.nextInt(SOURCE_LEN);

    // enrol this algorithm with the seed maker
    SEED_MAKER.submit(new Seed(random));

    // update the state
    synchronized (this) {
      available = BLOCK_LEN;
    }

    // Now at least one System Random is initialised, the Seed Storage can
    // start using SystemRandom for scrambling.
    SeedStorage.upgradeScrambler();
  }


  /**
   * Get more data from the system random number generator
   */
  @Override
  public void run() {
    if (canSeed) {
      // use injected seeds immediately
      byte[] s = INJECTED.poll();
      if (s != null) {
        doReseed(s);
      } else {
        reseed--;
      }

      // is a reseed due?
      if (reseed < 0) {
        // get the next seed
        Future<Seed> future = SEED_MAKER.poll();
        if (future != null) {
          // got a future, does it have a seed?
          Seed seed = null;
          try {
            seed = future.get();
          } catch (InterruptedException e) {
            LOG.debug("Seed generation was interrupted.");
          } catch (ExecutionException e) {
            LOG.error("Seed generation failed.", e.getCause());
          }

          if (seed != null) {
            // we are reseeding, schedule the next reseed
            reseed = RESEED.nextInt(SOURCE_LEN);

            // use the seed and then resubmit to get a future one
            doReseed(seed.seed);
            seed.resubmit();
          }
        }
      }
    }

    fetchBytes();
  }
}
