package prng;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import prng.collector.DaemonThreadFactory;
import prng.collector.InstantEntropy;
import prng.nist.HashSpec;
import prng.nist.NistHashRandom;
import prng.nist.SeedSource;
import prng.seeds.SeedStorage;

/**
 * System provided secure random sources. We assume there is at least one such
 * source. The sources are multiplexed, with one byte taken from each source in
 * turn. This means that if any source has a good entropic seed, its entropy
 * will be included in all outputs.
 * <p>
 * 
 * The sources are periodically cross-pollinated with entropy from each other.
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

    /** Queue for injected seeds */
    private static final LinkedBlockingQueue<byte[]> INJECTED = new LinkedBlockingQueue<>(
            100);

    /** Logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(SystemRandom.class);

    /**
     * Random number generator that draws from the System random number sources
     */
    private static SecureRandom RANDOM = null;

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
            return SystemRandom.getSeed(size);
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
                new SynchronousQueue<Runnable>(), new DaemonThreadFactory(
                        "PRNG-SystemRandom"));
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
     * @param size
     *            number of seed bytes required
     * @return the seed data
     */
    public static byte[] getSeed(int size) {
        byte[] data = new byte[size];
        int index = RESEED.nextInt(SOURCE_LEN);
        for(int i = 0;i < size;i++) {
            // try for a byte
            boolean needByte = true;
            for(int j = 0;needByte && (j < SOURCE_LEN);j++) {
                if( SOURCES[index].get(data, i) ) {
                    needByte = false;
                }
                index = (index + 1) % SOURCE_LEN;
            }

            // if no byte, get one from instant entropy
            if( needByte ) {
                byte[] b = InstantEntropy.SOURCE.getSeed(1);
                data[i] = b[0];
            }
        }
        return data;
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
     * Inject seed data into the system random number generators.
     * 
     * @param seed
     *            data to inject
     */
    public static void injectSeed(byte[] seed) {
        if( seed == null || seed.length == 0 ) return;

        // Offer it the injection queue.
        while( !INJECTED.offer(seed) ) {
            // did not go to queue, so combine some entries to make space
            DigestDataOutput out = new DigestDataOutput("SHA-512");
            out.writeInt(seed.length);
            out.write(seed);

            // attempt to remove 5 entries
            for(int i = 0;i < 5;i++) {
                byte[] s = INJECTED.poll();
                if( s != null ) {
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
     * @param rand
     *            byte array to fill
     */
    public static void nextBytes(byte[] rand) {
        getRandom().nextBytes(rand);
    }

    /**
     * Number of bytes available in the current block. A value of -1 means not
     * initialised.
     */
    private int available_ = -1;

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
            // is this initialised?
            if( available_ == -1 ) return false;

            // if no bytes available, cannot supply any
            if( available_ == 0 ) return false;

            // get a byte
            int p = (--available_);
            output[pos] = block_[p];

            // have we used all available bytes?
            if( p == 0 ) {
                if( LOG.isDebugEnabled() ) {
                    LOG.debug("Used all bytes from "
                            + random_.getProvider().getName() + ":"
                            + random_.getAlgorithm());
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
     * @param prov
     *            the provider
     * @param alg
     *            the algorithm
     */
    void init(Provider prov, String alg) {
        if( prov == null ) {
            // request the strong instance
            LOG.info("Initialising System strong PRNG");

            // We use reflection so that we can support Java 7
            try {
                Class<?> cl = SecureRandom.class;
                Method m = cl.getMethod("getInstanceStrong");
                random_ = (SecureRandom) m.invoke(null);
            } catch (NoSuchMethodException e) {
                // Not Java 8
                LOG.info("Need Java 8+ for strong system PRNG. Using default.");
            } catch (IllegalAccessException e) {
                LOG.error("Strong PRNG not accessible. Using default.", e);
            } catch (IllegalArgumentException e) {
                // not expected as we pass no arguments
                LOG.error("Strong PRNG threw exception. Using default.", e);
            } catch (InvocationTargetException e) {
                // Could be a NoSuchAlgorithm exception
                LOG.error("Strong PRNG could not be created. Using default.", e);
            }
            if( random_ == null ) random_ = new SecureRandom();
        } else {
            // get the specific instance
            LOG.info("Initialising System PRNG: {}:{}", prov.getName(), alg);
            try {
                random_ = SecureRandom.getInstance(alg, prov);
            } catch (NoSuchAlgorithmException e) {
                // fall-back to some instance
                LOG.error("Provider " + prov + " does not implement " + alg
                        + " after announcing it as a service");
                random_ = new SecureRandom();
            }
        }

        // load the first block
        random_.nextBytes(block_);

        // set when this reseeds
        reseed_ = RESEED.nextInt(SOURCE_LEN);

        // enrol this algorithm with the seed maker
        SEED_MAKER.submit(new Seed(random_));

        // update the state
        synchronized (this) {
            available_ = BLOCK_LEN;
        }

        // Now at least one System Random is initialised, the Seed Storage can
        // start using SystemRandom for scrambling.
        SeedStorage.upgradeScrambler();
    }


    /**
     * Get more data from the system random number generator
     */
    public void run() {
        // use injected seeds immediately
        byte[] s = INJECTED.poll();
        if( s != null ) {
            random_.setSeed(s);
        } else {
            reseed_--;
        }

        // is a reseed due?
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

        fetchBytes();
    }


    /**
     * Fetch new bytes
     */
    private void fetchBytes() {
        synchronized (this) {
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
            available_ = BLOCK_LEN;
        }
    }
}
