package prng.collector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import prng.DigestDataOutput;
import prng.NonceFactory;
import prng.SystemRandom;
import prng.nist.HashSpec;
import prng.nist.IsaacRandom;
import prng.nist.SeedSource;

/**
 * Attempts to create useful entropy from nothing. It should be assumed that
 * this entropy is of low quality and therefore it should only be used a last
 * recourse.
 *
 * @author Simon Greatrix
 */
public class InstantEntropy implements Runnable {

    /**
     * Holder for some entropy that will be derived at some point in the future.
     *
     * @author Simon Greatrix
     *
     */
    static class Holder implements Runnable {
        /** The entropy */
        byte[] entropy_ = null;


        /**
         * Get the entropy, waiting for it to arrive.
         *
         * @return the entropy
         */
        public byte[] get(long millis) throws InterruptedException {
            synchronized (this) {
                while( entropy_ == null ) {
                    wait(millis);
                }
                return entropy_;
            }
        }


        /**
         * Reset this holder to empty
         */
        public void reset() {
            synchronized (this) {
                entropy_ = null;
            }
            FUTURE_RUNNER.submit(this);
        }


        @Override
        public void run() {
            boolean isSet = false;
            try {
                byte[] b = create();
                set(b);
                isSet = true;
            } finally {
                if( !isSet ) {
                    set(null);
                }
            }
        }


        /**
         * Set the entropy
         *
         * @param entropy
         *            the entropy.
         */
        public void set(byte[] entropy) {
            if( entropy_ == null ) entropy_ = new byte[0];
            synchronized (this) {
                entropy_ = entropy;
                notifyAll();
            }
        }


        /**
         * Try and get the entropy right now.
         *
         * @return the entropy or null if it has not yet arrived
         */
        public byte[] tryGet() {
            synchronized (this) {
                return entropy_;
            }
        }
    }

    /**
     * All prime numbers greater than 30 take the form of 30k+c, where c is one
     * of these values. Of course, not all numbers of the form 30k+c are prime!
     */
    private static final int[] ADD_CONST = new int[] { 1, 7, 11, 13, 17, 19,
            23, 29 };

    private static ExecutorService ENTROPY_RUNNER = new ThreadPoolExecutor(20,
            20, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    /** Mask for 256-bit FNV hash */
    private static final BigInteger FNV_MASK = BigInteger.ZERO.flipBit(256);

    /** Offset for 256-bit FNV hash */
    private static final BigInteger FNV_OFFSET = new BigInteger(
            "100029257958052580907070968620625704837092796014241193945225284501741471925557");

    /** Prime for 256-bit FNV hash */
    private static final BigInteger FNV_PRIME = new BigInteger(
            "374144419156711147060143317175368453031918731002211");

    private static ExecutorService FUTURE_RUNNER = new ThreadPoolExecutor(2, 2,
            1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    /**
     * A random number generator. This is a secure algorithm, but its seed
     * information is only the instant entropy we are able to create.
     */
    public static final IsaacRandom RAND = new IsaacRandom();

    /** An "instant" entropy source */
    public static final SeedSource SOURCE = new SeedSource() {
        /** Current batch of entropy */
        byte[] entropy_ = new byte[0];

        /** Current position in this entropy batch */
        int pos_ = 0;


        @Override
        public byte[] getSeed(int size) {
            int offset = 0;
            int len = size;
            byte[] output = new byte[size];
            synchronized (this) {
                while( len > 0 ) {
                    if( pos_ >= entropy_.length ) {
                        entropy_ = get();
                        pos_ = 0;
                    }

                    int rem = entropy_.length - pos_;
                    if( rem <= len ) {
                        System.arraycopy(output, offset, entropy_, pos_, rem);
                        len -= rem;
                        pos_ += rem;
                    } else {
                        System.arraycopy(output, offset, entropy_, pos_, len);
                        len = 0;
                        pos_ += len;
                    }
                }
            }
            return output;
        }
    };

    private static final Holder[] STORE = new Holder[64];

    static {
        // start off with our personalization value
        setSeed(NonceFactory.personalization());

        ((ThreadPoolExecutor) FUTURE_RUNNER).allowCoreThreadTimeOut(true);
        ((ThreadPoolExecutor) ENTROPY_RUNNER).allowCoreThreadTimeOut(true);

        // Initialise the seed store in a random order. This makes it less
        // likely we will hit seed's created adjacently when we are looking for
        // any created seed early on.
        int[] p = permute(64);
        for(int i = 0;i < 64;i++) {
            Holder h = new Holder();
            STORE[p[i]] = h;
            h.reset();
        }
    }


    /**
     * Create some entropy.
     *
     * @return some entropy
     */
    static byte[] create() {
        DigestDataOutput dig = new DigestDataOutput(
                HashSpec.SPEC_SHA512.getInstance());

        int[] p = permute(256);
        CountDownLatch latch = new CountDownLatch(256);
        for(int i = 0;i < 256;i++) {
            ENTROPY_RUNNER.submit(new InstantEntropy(p[i], latch, dig));
        }
        try {
            latch.await();
        } catch (InterruptedException ie) {
            EntropyCollector.LOG.warn("Unexpected interrupt", ie);
            StringWriter sw = new StringWriter();
            ie.printStackTrace(new PrintWriter(sw));
            synchronized (dig) {
                dig.writeUTF(sw.toString());
            }
        }
        byte[] out;
        synchronized (dig) {
            out = dig.digest();
        }
        setSeed(out);
        SystemRandom.injectSeed(out);
        return out;
    }


    /**
     * Get some entropy.
     *
     * @return some entropy
     */
    public static byte[] get() {
        int id = 0;
        byte[] ret = null;
        try {
            synchronized (STORE) {
                // First look for some ready entropy. This will try about 40 of
                // the 64 slots.
                for(int i = 0;(i < 64) && (ret == null);i++) {
                    id = RAND.nextInt(64);
                    ret = STORE[id].tryGet();
                }

                // If no luck yet, stop eating CPU with a busy spin.
                while( ret == null ) {
                    id = RAND.nextInt(64);
                    ret = STORE[id].get(10);
                }
                STORE[id].reset();
            }
            return ret;
        } catch (InterruptedException e) {
            EntropyCollector.LOG.warn("Entropy generation interrupted", e);
        }
        return new byte[0];
    }


    /**
     * Get some entropy
     *
     * @return some entropy
     */

    /**
     * Permute the numbers 0 .. (size-1).
     *
     * @param size
     *            the number of integers to permute
     * @return the permutation
     */
    private static int[] permute(int size) {
        int[] p = new int[size];
        Arrays.fill(p, -1);
        for(int i = 0;i < size;i++) {
            int j = RAND.nextInt(size);
            while( p[j] != -1 ) {
                j = (j + 1) % size;
            }
            p[j] = i;
        }
        return p;
    }


    /**
     * Reset the seed on the ISAAC random number generator
     *
     * @param p
     *            new seed
     */
    private static void setSeed(byte[] p) {
        // We are going to create an ISAAC secure random generator. ISAAC takes
        // up to a 1024 byte seed. We try to create those 1024 bytes using our
        // very limited supply of immediate entropy.

        // Create a 1024 byte entropy source
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.put(p, 0, Math.min(1024, p.length));

        // Use a 256-bit (32 byte) FNV-1a Hash
        while( buf.hasRemaining() ) {
            // hash the previous value
            BigInteger hash = FNV_OFFSET;
            for(int j = 0;j < p.length;j++) {
                hash = hash.xor(BigInteger.valueOf(0xff & p[j])).multiply(
                        FNV_PRIME).mod(FNV_MASK);
            }

            // Add the nano time to the hash. As we are not doing anything
            // variable in this loop the nano time will be like a counter. If we
            // are lucky there will be a slight variation. Primarily though this
            // is like creating pseudo random numbers by using a cipher in
            // counter mode.
            long now = System.nanoTime();
            for(int j = 0;j < 8;j++) {
                hash = hash.xor(BigInteger.valueOf(0xff & now)).multiply(
                        FNV_PRIME).mod(FNV_MASK);
                now >>>= 8;
            }

            // convert hash to byte array
            p = hash.toByteArray();

            int len = Math.min(buf.remaining(), p.length);
            buf.put(p, p.length - len, len);
        }

        // Now get a "random" bit. It's the bit above the least significant
        // non-zero bit in the nano time
        long now = System.nanoTime();
        int bit = Long.numberOfTrailingZeros(now);
        now >>>= (bit + 1);
        buf.order((now & 1) == 0 ? ByteOrder.BIG_ENDIAN
                : ByteOrder.LITTLE_ENDIAN);

        // Permute the bytes. This breaks up the blocks used to create the seed
        // and used by ISAAC to interpret it.
        byte[] mask = new byte[1024];
        for(int i = 0;i < 1024;i++) {
            int j = RAND.nextInt(1024);
            byte ti = buf.get(i);
            byte tj = buf.get(j);
            buf.put(i, tj);
            buf.put(j, ti);
        }

        // Encrypt the bytes. This mixes the existing seed information with the
        // new seed.
        RAND.nextBytes(mask);
        for(int i = 0;i < 1024;i++) {
            byte ti = (byte) (buf.get(i) ^ mask[i]);
            buf.put(i, ti);
        }

        // convert to ints
        buf.position(0).limit(1024);
        IntBuffer ibuf = buf.asIntBuffer();
        int[] seed = new int[256];
        ibuf.get(seed);

        // seed the ISAAC random generator with this entropy
        RAND.setSeed(seed);
    }

    /**
     * This generator's ID
     */
    private final int id_;

    /** Synchronizing latch */
    private final CountDownLatch latch_;

    /** The entropy output sink */
    private final DigestDataOutput output_;

    /**
     * Time this generator started
     */
    private final long startTime_ = System.nanoTime();


    /**
     * Create a thread that could generate some entropy
     *
     * @param id
     *            an ID
     * @param output
     *            the output sink
     */
    InstantEntropy(int id, CountDownLatch latch, DigestDataOutput output) {
        id_ = id;
        latch_ = latch;
        output_ = output;
    }


    /**
     * Find prime numbers. As prime numbers are scattered without pattern and
     * our starting points are from a cryptographically secure PRNG, the time it
     * takes to find such prime numbers should be difficult to predict. We also
     * consider which thread produces the prime number, the number itself and
     * the time it takes to do so as useful entropy.
     * <p>
     *
     * Each execution of this method tests one number to see if it is prime. If
     * it is not, this task is resubmitted. Otherwise it writes out its entropy
     * and terminates.
     */
    @Override
    public void run() {
        synchronized (output_) {
            output_.writeBoolean(true);
            output_.write(id_);
            output_.writeLong(Thread.currentThread().getId());
        }

        int p = tryFindPrime();
        if( p == -1 ) {
            // Did not find a prime this time, so resubmit
            ENTROPY_RUNNER.submit(this);
            return;
        }

        // We did find a prime
        long e = System.nanoTime() - startTime_;

        // write out entropy
        synchronized (output_) {
            output_.writeBoolean(false);
            output_.write(id_);
            output_.writeLong(Thread.currentThread().getId());
            output_.writeInt(p);
            output_.writeInt((int) e);
        }
        latch_.countDown();
    }


    /**
     * Try to find a prime number. This is simply an operation that takes a
     * hard-to-predict amount of time with a hard-to-predict output.
     *
     * @return the prime number, or -1
     */
    int tryFindPrime() {
        // Create a candidate that is not divisible by 2,3 or 5
        int v = 1 + RAND.nextInt(0x2000000);
        int p = 30 * (v >>> 3) + ADD_CONST[v & 0x7];

        // check it does not divide by any other prime <30
        for(int i = 1;i < 8;i++) {
            if( (p % ADD_CONST[i]) == 0 ) {
                // not a prime
                return -1;
            }
        }

        // check up to square root
        int m = (int) (Math.sqrt(p) / 30);
        for(int j = 1;j < m;j++) {
            for(int i = 0;i < 8;i++) {
                int d = 30 * j + ADD_CONST[i];
                if( (p % d) == 0 ) {
                    // not a prime
                    return -1;
                }
            }
        }

        // found a prime
        return p;
    }
}