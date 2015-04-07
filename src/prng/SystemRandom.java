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

/**
 * System provided secure random sources. We assume there is at least one such
 * source. The sources are multiplexed, with one byte taken from each source in
 * turn. This means that if any source has a good entropic seed, its entropy
 * will be included in all outputs.
 * <p>
 * 
 * Note that since some system provided random sources will block whilst they
 * wait for entropy to arrive (e.g. reading from /dev/random), this class may
 * delay start-up.
 * 
 * @author Simon Greatrix
 *
 */
public class SystemRandom implements Runnable {
    public static void main(String[] args) {
        for(int i = 0;i < 10000;i++) {
            System.out.println(getRandom().nextLong());
        }
    }

    /**
     * A seed from one of the system sources
     * 
     * @author Simon Greatrix
     */
    class Seed implements Callable<Seed> {
        /** Generated seed */
        byte[] seed_;


        @Override
        public Seed call() {
            if( LOG.isDebugEnabled() ) {
                LOG.debug("Generating seed from "
                        + random_.getProvider().getName() + ":"
                        + random_.getAlgorithm());
            }
            seed_ = random_.generateSeed(32);
            System.out.println("Finished generating seed from "
                    + random_.getProvider().getName() + ":"
                    + random_.getAlgorithm());
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
            SEED_MAKER.submit(this);
        }
    }

    /**
     * Thread pool executor
     */
    private static final Executor EXECUTOR;

    /**
     * Fetching seed data may block. To prevent waits on re-seeding we use this
     * completion service.
     */
    private static final ExecutorCompletionService<Seed> SEED_MAKER;

    /** Logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(SystemRandom.class);

    /** Block length that is fetched from each source at one time */
    private static final int BLOCK_LEN = 256;

    /**
     * "Random" selection for which source gets reseeded. The intention is to
     * all sources of seed data to influence all other sources by "randomly"
     * assigning seed data to a source.
     */
    private static Random RESEED = new Random();

    /** Current source in pre-fetched data */
    private static int INDEX = 0;

    /**
     * Random number generator that draws from the System random number sources
     */
    private static SecureRandom RANDOM = null;

    /** Number of sources */
    private static final int SOURCE_LEN;

    /** System provided secure random number generators */
    private final static SystemRandom[] SOURCES;

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
     * Get random data from the combined system random number generators
     * 
     * @param rand
     *            byte array to fill
     */
    public static void nextBytes(byte[] rand) {
        getRandom().nextBytes(rand);
    }

    private SecureRandom random_ = null;

    private int reseed_;

    private byte[] block_ = new byte[BLOCK_LEN];

    private int available_ = 0;

    private static int READY = 0;


    SystemRandom(final Provider prov, final String alg) {
        EXECUTOR.execute(new Runnable() {
            public void run() {
                SystemRandom.this.init(prov, alg);
            }
        });
    }


    /**
     * Asynchronously initialise this
     */
    void init(Provider prov, String alg) {
        if( prov == null ) {
            LOG.info("Initialising System strong PRNG");
            try {
                random_ = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                LOG.error(
                        "System strong secure random generator is unavailable.",
                        e);
                random_ = new SecureRandom();
            }
        } else {
            System.out.println(alg);
            String n = prov.getName();
            System.out.println(n);
            LOG.info("Initialising System PRNG: {}:{}", prov.getName(), alg);
            try {
                random_ = SecureRandom.getInstance(alg, prov);
            } catch (NoSuchAlgorithmException e) {
                LOG.error("Provider " + prov + " does not implement " + alg
                        + " after announcing it as a service");
                random_ = new SecureRandom();
            }
        }

        random_.nextBytes(block_);
        SEED_MAKER.submit(new Seed());
        reseed_ = RESEED.nextInt(SOURCE_LEN);
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
        reseed_--;
        if( reseed_ < 0 ) {
            reseed_ = RESEED.nextInt(SOURCE_LEN);
            Future<Seed> future = SEED_MAKER.poll();
            if( future != null ) {
                Seed seed = null;
                try {
                    seed = future.get();
                } catch (InterruptedException e) {
                    LOG.debug("Seed generation was interrupted.");
                } catch (ExecutionException e) {
                    LOG.error("Seed generation failed.", e.getCause());
                }
                if( seed != null ) {
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
        random_.nextBytes(block_);
        if( LOG.isDebugEnabled() ) {
            LOG.debug("Finished generating bytes from "
                    + random_.getProvider().getName() + ":"
                    + random_.getAlgorithm());
        }
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
}
