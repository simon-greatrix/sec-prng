package prng;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Provider.Service;
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
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.nist.HashSpec;
import prng.nist.NistHashRandom;
import prng.nist.SeedSource;

/**
 * System provided secure random sources. We assume there is at least one such
 * source. The sources are multiplexed, with one byte taken from each source in
 * turn. This means that if any source has a good entropic seed, its entropy
 * will be included in all outputs.
 * <p>
 * 
 * The sources are periodically cross-polinated with entropy from each other.
 * <p>
 * 
 * Note that since some system provided random sources will block whilst they
 * wait for entropy to arrive (e.g. reading from /dev/random), this class may
 * delay start-up.
 * <p>
 * 
 * @author Simon Greatrix
 *
 */
public class SystemRandom implements Runnable {
    /**
     * A seed from one of the system sources
     * 
     * @author Simon Greatrix
     */
    static class Seed implements Callable<Seed> {
        /** Secure random seed source */
        final SecureRandom random_;

        /** Generated or injected seed */
        byte[] seed_ = null;


        /**
         * Inject seed data
         * 
         * @param seed
         *            data to inject
         */
        Seed(byte[] seed) {
            random_ = null;
            seed_ = seed.clone();
        }


        /**
         * Create seeds from the supplied PRNG
         * 
         * @param random
         *            seed source
         */
        Seed(SecureRandom random) {
            random_ = random;
        }


        @Override
        public Seed call() {
            if( random_ == null ) return this;

            if( LOG.isDebugEnabled() ) {
                LOG.debug("Generating seed from "
                        + random_.getProvider().getName() + ":"
                        + random_.getAlgorithm());
            }
            seed_ = random_.generateSeed(32);
            if( LOG.isDebugEnabled() ) {
                LOG.debug("Finished generating seed from "
                        + random_.getProvider().getName() + ":"
                        + random_.getAlgorithm());
            }
            return this;
        }


        /**
         * Resubmit this seed source
         */
        public void resubmit() {
            seed_ = null;
            if( random_ != null ) {
                SEED_MAKER.submit(this);
            }
        }
    }

    /** Block length that is fetched from each source at one time */
    private static final int BLOCK_LEN = 256;

    /**
     * Thread pool executor
     */
    private static final Executor EXECUTOR;

    /** Current source in pre-fetched data */
    private static int INDEX = 0;

    /** Logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(SystemRandom.class);

    /**
     * Random number generator that draws from the System random number sources
     */
    private static SecureRandom RANDOM = null;

    /**
     * The number of currently ready sources. Requests will block if there are
     * none.
     */
    private static int READY = 0;

    /**
     * "Random" selection for which source gets reseeded. The intention is to
     * all sources of seed data to influence all other sources by "randomly"
     * assigning seed data to a source.
     */
    private static Random RESEED = new Random();

    /**
     * Fetching seed data may block. To prevent waits on re-seeding we use this
     * completion service.
     */
    private static final ExecutorCompletionService<Seed> SEED_MAKER;

    /**
     * Source for getting entropy from the system
     */
    public static final SeedSource SOURCE = new SeedSource() {
        @Override
        public byte[] getSeed(int size) {
            byte[] seed = new byte[size];
            SystemRandom.generateSeed(seed);
            return seed;
        }
    };

    /** Number of sources */
    private static final int SOURCE_LEN;

    /** System provided secure random number generators */
    private final static SystemRandom[] SOURCES;

    static {
        Provider[] provs = Security.getProviders();
        ArrayList<Service> servs = new ArrayList<>();
        for(Provider prov:provs) {
            // do not loop into our own provider
            if( prov instanceof SecureRandomProvider ) continue;

            // check for offered secure random sources
            Set<Service> serv = prov.getServices();
            for(Service s:serv) {
                if( s.getType().equals("SecureRandom") ) {
                    servs.add(s);
                }
            }
        }

        // Now we know how many services we have, initialise arrays
        int len = 1 + servs.size();
        SOURCE_LEN = len;
        SOURCES = new SystemRandom[len];
        EXECUTOR = new ThreadPoolExecutor(0, 2 * len, 0, TimeUnit.NANOSECONDS,
                new SynchronousQueue<Runnable>());
        SEED_MAKER = new ExecutorCompletionService<Seed>(EXECUTOR);

        // create the PRNGs
        for(int i = 0;i < (len - 1);i++) {
            Service s = servs.get(i);
            LOG.debug("Found system PRNG " + s.getProvider().getName() + ":"
                    + s.getAlgorithm());
            SOURCES[i] = new SystemRandom(s.getProvider(), s.getAlgorithm());
        }

        // always include the "strong" algorithm
        SOURCES[len - 1] = new SystemRandom(null, null);
    }


    /**
     * Get seed data from the system secure random number generators. This data
     * is drawn from the output of the system secure random number generators,
     * not their actual entropy sources.
     * 
     * @param data
     *            array to fill
     */
    public static void generateSeed(byte[] data) {
        synchronized (SOURCES) {
            int startIndex = INDEX;
            int index = startIndex;
            boolean gotOne = false;
            for(int i = 0;i < data.length;i++) {
                // try for a byte
                if( SOURCES[index].get(data, i) ) {
                    gotOne = true;
                }

                // next source
                index = (index + 1) % SOURCE_LEN;
                if( index == startIndex && !gotOne ) {
                    while( READY == 0 ) {
                        try {
                            LOG.info("Waiting for more system random bytes");
                            SOURCES.wait();
                        } catch (InterruptedException ie) {
                            LOG.info("Seed generation was interrupted");
                            byte[] failSafe = new byte[data.length - i];
                            RESEED.nextBytes(failSafe);
                            System.arraycopy(failSafe, 0, data, i,
                                    failSafe.length);
                            return;
                        }
                    }
                }
            }
            INDEX = index;
        }
    }


    /**
     * Get a SecureRandom instance that draws upon the system secure PRNGs for
     * seed entropy. The SecureRandom is based upon a NIST algorithm and will
     * reseed itself with additional entropy after every operation.
     * 
     * @return a SecureRandom instance.
     */
    public static SecureRandom getRandom() {
        SecureRandom rand = RANDOM;
        if( rand == null ) {
            rand = new SecureRandomImpl(new NistHashRandom(SOURCE,
                    HashSpec.SPEC_SHA512, 0, null, null, null));
            RANDOM = rand;
        }
        return rand;
    }


    /**
     * Inject seed data into the system random number generators
     * 
     * @param seed
     *            data to inject
     */
    public static void injectSeed(byte[] seed) {
        if( seed == null || seed.length == 0 ) return;
        Seed s = new Seed(seed);
        SEED_MAKER.submit(s);
    }


    /**
     * Get random data from the combined system random number generators
     * 
     * @param rand
     *            byte array to fill
     */
    public static void nextBytes(byte[] rand) {
        getRandom().nextBytes(rand);
    }

    /** Number of bytes available in the current block */
    private int available_ = 0;

    /** Random bytes drawn from the system PRNG */
    private byte[] block_ = new byte[BLOCK_LEN];

    /** The System SecureRandom instance */
    private SecureRandom random_ = null;

    /** Number of operations before requesting a reseed */
    private int reseed_;


    /**
     * Create a new instance using the specified provider and algorithm. If the
     * provider is null, the system "strong" algorithm will be requested.
     * Initialisation will be undertaken asynchronously to prevent blocking.
     * 
     * @param prov
     *            the provider
     * @param alg
     *            the algorithm
     */
    SystemRandom(final Provider prov, final String alg) {
        EXECUTOR.execute(new Runnable() {
            public void run() {
                SystemRandom.this.init(prov, alg);
            }
        });
    }


    /**
     * Get one byte from this generator, if possible
     * 
     * @param output
     *            the output array
     * @param pos
     *            where to put the byte
     * @return true if a byte could be supplied
     */
    boolean get(byte[] output, int pos) {
        synchronized (this) {
            if( available_ == 0 ) return false;
            int p = (--available_);
            if( p == 0 ) {
                if( LOG.isDebugEnabled() ) {
                    LOG.debug("Used all bytes from "
                            + random_.getProvider().getName() + ":"
                            + random_.getAlgorithm());
                }
                synchronized (SOURCES) {
                    READY--;
                }
                EXECUTOR.execute(this);
            }
            output[pos] = block_[p];
        }
        return true;
    }


    /**
     * Asynchronously initialise this.
     * 
     * @param prov
     *            the provider
     * @param alg
     *            the algorithm
     */
    void init(Provider prov, String alg) {
        if( prov == null ) {
            // request the strong instance
            LOG.info("Initialising System strong PRNG");
            try {
                random_ = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                // fallback to some instance
                LOG.error(
                        "System strong secure random generator is unavailable.",
                        e);
                random_ = new SecureRandom();
            }
        } else {
            // get the specific instance
            LOG.info("Initialising System PRNG: {}:{}", prov.getName(), alg);
            try {
                random_ = SecureRandom.getInstance(alg, prov);
            } catch (NoSuchAlgorithmException e) {
                // fallback to some instance
                LOG.error("Provider " + prov + " does not implement " + alg
                        + " after announcing it as a service");
                random_ = new SecureRandom();
            }
        }

        // load the first block
        random_.nextBytes(block_);

        // enroll this algorithm with the seed maker
        SEED_MAKER.submit(new Seed(random_));

        // set when this reseeds
        reseed_ = RESEED.nextInt(SOURCE_LEN);

        // update the state
        synchronized (this) {
            available_ = BLOCK_LEN;
            synchronized (SOURCES) {
                READY++;
                SOURCES.notifyAll();
            }
            notifyAll();
        }
    }


    /**
     * Get more data from the system random number generator
     */
    public void run() {
        // see if we need to reseed before generating more random bytes
        reseed_--;
        if( reseed_ < 0 ) {
            // get the next seed
            Future<Seed> future = SEED_MAKER.poll();
            if( future != null ) {
                // got a future, does it have a seed?
                Seed seed = null;
                try {
                    seed = future.get();
                } catch (InterruptedException e) {
                    LOG.debug("Seed generation was interrupted.");
                } catch (ExecutionException e) {
                    LOG.error("Seed generation failed.", e.getCause());
                }

                if( seed != null ) {
                    // we are reseeding, schedule the next reseed
                    reseed_ = RESEED.nextInt(SOURCE_LEN);

                    // use the seed and then resubmit to get a future one
                    random_.setSeed(seed.seed_);
                    seed.resubmit();
                }
            }
        }

        if( LOG.isDebugEnabled() ) {
            LOG.debug("Generating bytes from "
                    + random_.getProvider().getName() + ":"
                    + random_.getAlgorithm());
        }

        // generate random bytes
        random_.nextBytes(block_);

        if( LOG.isDebugEnabled() ) {
            LOG.debug("Finished generating bytes from "
                    + random_.getProvider().getName() + ":"
                    + random_.getAlgorithm());
        }

        // update the state
        synchronized (this) {
            available_ = BLOCK_LEN;
            synchronized (SOURCES) {
                READY++;
                SOURCES.notifyAll();
            }
            notifyAll();
        }
    }
}
