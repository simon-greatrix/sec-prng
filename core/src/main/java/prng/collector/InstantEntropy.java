package prng.collector;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import prng.SystemRandom;
import prng.generator.HashSpec;
import prng.generator.IsaacRandom;
import prng.generator.SeedSource;
import prng.seeds.Seed;
import prng.seeds.SeedStorage;
import prng.utility.DigestDataOutput;
import prng.utility.NonceFactory;

/**
 * Attempts to create useful entropy from nothing. It should be assumed that this entropy is of low quality, and therefore it should only be used a last
 * recourse.
 *
 * @author Simon Greatrix
 */
public class InstantEntropy implements Runnable {

  /** An "instant" entropy source */
  public static final SeedSource SOURCE = new Result();

  /** Count of ready sources */
  static final Counter COUNTER = new Counter();

  /** Thread pool for handling requests for entropy */
  static final Executor FUTURE_RUNNER = new ThreadPoolExecutor(
      2, 2,
      100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
      new DaemonThreadFactory("PRNG-SeedGenerator")
  );

  /**
   * A random number generator. This is a secure algorithm, but its seed information is only the instant entropy we are able to create.
   */
  static final IsaacRandom RAND = IsaacRandom.getSharedInstance();

  /**
   * All prime numbers greater than 30 take the form of 30k+c, where c is one of these values. Of course, not all numbers of the form 30k+c are prime!
   */
  private static final int[] ADD_CONST = {1, 7, 11, 13, 17, 19, 23, 29};

  /** Thread pool for generating entropy */
  private static final ExecutorService ENTROPY_RUNNER = new ThreadPoolExecutor(
      20,
      20, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
      new DaemonThreadFactory("PRNG-EntropyFactory")
  );

  /** Bit mask for 256-bit FNV hash */
  private static final BigInteger FNV_MASK = BigInteger.ZERO.setBit(256).subtract(BigInteger.ONE);

  /** Offset for 256-bit FNV hash */
  private static final BigInteger FNV_OFFSET = new BigInteger("100029257958052580907070968620625704837092796014241193945225284501741471925557");

  /** Prime for 256-bit FNV hash */
  private static final BigInteger FNV_PRIME = new BigInteger("374144419156711147060143317175368453031918731002211");

  /**
   * Stored entropy. We store 64 blocks of entropy and release them in a random order. There are much more than 2^256 ways of permuting 64 items, so this may
   * add another layer of security to the implementation.
   */
  private static final Holder[] STORE = new Holder[64];



  /**
   * Count of ready entropy blocks
   *
   * @author Simon Greatrix
   */
  static class Counter {

    /** The current count of ready blocks */
    private int count = 0;

    /** Number of entropy updates */
    private int updates = 0;


    /**
     * Decrement the count
     */
    public void decrement() {
      synchronized (this) {
        count--;
      }
    }


    /**
     * Increment the count
     */
    public void increment() {
      synchronized (this) {
        count++;

        // every 64 updates, save the new ISAAC entropy.
        updates++;
        if (updates >= 64) {
          updates = 0;

          // Save the ISAAC entropy.
          FUTURE_RUNNER.execute(() -> {
            byte[] data = new byte[1024];
            RAND.nextBytes(data);
            Seed seed = new Seed("instant", data);
            SeedStorage.enqueue(seed);
          });
        }

        // notify any waiting threads of new entropy
        notifyAll();
      }
    }


    /**
     * Look for some ready entropy
     *
     * @throws InterruptedException if the current thread is interrupted before entropy arrives
     */
    public void lookFor() throws InterruptedException {
      synchronized (this) {
        if (count > 0) {
          return;
        }
        wait();
      }

    }

  }



  /**
   * Holder for some entropy that will be derived at some point in the future.
   *
   * @author Simon Greatrix
   */
  static class Holder implements Runnable {

    /** The entropy */
    byte[] entropy = null;


    /**
     * Get the entropy, waiting for it to arrive.
     *
     * @param millis maximum time to wait for entropy
     *
     * @return the entropy
     *
     * @throws InterruptedException if the current thread is interrupted before the entropy arrives
     */
    public byte[] get(long millis) throws InterruptedException {
      synchronized (this) {
        while (entropy == null) {
          wait(millis);
        }
        return entropy;
      }
    }


    /**
     * Reset this holder to empty
     */
    public void reset() {
      synchronized (this) {
        if (entropy != null) {
          COUNTER.decrement();
          entropy = null;
        }
      }
      FUTURE_RUNNER.execute(this);
    }


    @Override
    public void run() {
      boolean isSet = false;
      try {
        byte[] b = create();
        set(b);
        isSet = true;
      } finally {
        // if something went wrong, still set entropy
        if (!isSet) {
          set(null);
        }
      }
    }


    /**
     * Set the entropy
     *
     * @param newEntropy the entropy.
     */
    public void set(byte[] newEntropy) {
      if (newEntropy == null) {
        newEntropy = new byte[0];
      }
      synchronized (this) {
        byte[] oldEntropy = entropy;
        entropy = newEntropy;
        if (oldEntropy == null) {
          COUNTER.increment();
          notifyAll();
        }
      }
    }


    /**
     * Try and get the entropy right now.
     *
     * @return the entropy or null if it has not yet arrived
     */
    public byte[] tryGet() {
      synchronized (this) {
        return entropy;
      }
    }

  }



  /**
   * A seed source that holds the result of entropy generation and releases it as it is requested.
   *
   * @author Simon Greatrix
   */
  static class Result implements SeedSource {

    /** Current batch of entropy */
    byte[] entropy = new byte[0];

    /** Current position in this entropy batch */
    int pos = 0;


    @Override
    public String getName() {
      return "InstantEntropy";
    }


    @Override
    public byte[] getSeed(int size) {
      int offset = 0;
      int len = size;
      byte[] output = new byte[size];
      synchronized (this) {
        while (len > 0) {
          // do we need more entropy?
          if (pos >= entropy.length) {
            entropy = get();
            pos = 0;
          }

          int rem = entropy.length - pos;
          if (rem <= len) {
            // insufficient bytes remain in current batch
            System.arraycopy(output, offset, entropy, pos, rem);
            pos += rem;
            len -= rem;
          } else {
            // we have enough bytes in this batch
            System.arraycopy(output, offset, entropy, pos, len);
            pos += len;
            len = 0;
          }
        }
      }
      return output;
    }

  }


  /**
   * Create some entropy.
   *
   * @return some entropy
   */
  static byte[] create() {
    DigestDataOutput dig = new DigestDataOutput(HashSpec.SPEC_SHA512.getInstance());

    // generate 256 samples in some order
    int[] p = permute(256);
    CountDownLatch latch = new CountDownLatch(256);
    for (int i = 0; i < 256; i++) {
      ENTROPY_RUNNER.submit(new InstantEntropy(p[i], latch, dig));
    }

    // wait for the samples to be generated
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

    // Get the entropy. It is necessary to synchronize because if we were interrupted the entropy generation may still be ongoing.
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
    int id;
    byte[] ret = null;
    try {
      synchronized (STORE) {
        // Look for some ready entropy.
        do {
          COUNTER.lookFor();
          id = RAND.nextInt(64);
          for (int i = 0; (ret == null) && (i < 64); i++) {
            id = (id + 1) & 63;
            ret = STORE[id].tryGet();
          }
        }
        while (ret == null);

        STORE[id].reset();
      }
      return ret;
    } catch (InterruptedException e) {
      EntropyCollector.LOG.warn("Entropy generation interrupted", e);
    }
    return new byte[0];
  }


  /**
   * Permute the numbers 0 ... (size-1).
   *
   * @param size the number of integers to permute
   *
   * @return the permutation
   */
  private static int[] permute(int size) {
    int[] p = new int[size];
    Arrays.fill(p, -1);
    for (int i = 0; i < size; i++) {
      int j = RAND.nextInt(size);
      while (p[j] != -1) {
        j = (j + 1) % size;
      }
      p[j] = i;
    }
    return p;
  }


  /**
   * Reset the seed on the ISAAC random number generator
   *
   * @param p new seed
   */
  private static void setSeed(byte[] p) {
    // We are going to create an ISAAC secure random generator. ISAAC takes
    // up to a 1024 byte seed. We try to create those 1024 bytes using our
    // very limited supply of immediate entropy.

    // Create a 1024 byte entropy source
    ByteBuffer buf = ByteBuffer.allocate(1024);
    buf.put(p, 0, Math.min(1024, p.length));

    // Use a 256-bit (32 byte) FNV-1a Hash
    while (buf.hasRemaining()) {
      // hash the previous value
      BigInteger hash = FNV_OFFSET;
      for (byte b : p) {
        hash = hash.xor(BigInteger.valueOf(0xff & b)).multiply(FNV_PRIME).and(FNV_MASK);
      }

      // Add the nano time to the hash. As we are not doing anything
      // variable in this loop the nano time will be like a counter. If we
      // are lucky there will be a slight variation. Primarily though this
      // is like creating pseudo random numbers by using a cipher in
      // counter mode.
      long now = System.nanoTime();
      for (int j = 0; j < 8; j++) {
        hash = hash.xor(BigInteger.valueOf(0xff & now)).multiply(FNV_PRIME).and(FNV_MASK);
        now >>>= 8;
      }

      // Convert hash to byte array. Setting bit 256 makes the output one
      // byte too long (33 bytes), but it ensures that the hash never has
      // leading zeros and the sign bit is in the extra byte.
      p = hash.setBit(256).toByteArray();
      int len = Math.min(buf.remaining(), 32);
      buf.put(p, 33 - len, len);
    }

    // Now get a "random" bit. It's the bit above the least significant
    // non-zero bit in the nano time
    long now = System.nanoTime();
    int bit = Long.numberOfTrailingZeros(now);
    now >>>= (bit + 1);
    buf.order((now & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

    // Permute the bytes. This breaks up the blocks used to create the seed and used by ISAAC to interpret it.
    for (int i = 0; i < 1024; i++) {
      int j = RAND.nextInt(1024);
      byte ti = buf.get(i);
      byte tj = buf.get(j);
      buf.put(i, tj);
      buf.put(j, ti);
    }

    // convert to integers
    buf.position(0).limit(1024);
    IntBuffer ibuf = buf.asIntBuffer();
    int[] seed = new int[256];
    ibuf.get(seed);

    // seed the ISAAC random generator with this entropy
    RAND.setSeed(seed);
  }


  /**
   * Try to find a prime number. This is simply an operation that takes a hard-to-predict amount of time with a hard-to-predict output.
   *
   * @return the prime number, or -1
   */
  static int tryFindPrime() {
    // Create a candidate that is not divisible by 2,3 or 5. Approximately
    // 1/3 of these candidates are prime.
    int v = 1 + RAND.nextInt(0x10000);
    int p = 30 * (v >>> 3) + ADD_CONST[v & 0x7];

    // check it does not divide by any other prime <30
    for (int i = 1; i < 8; i++) {
      if ((p % ADD_CONST[i]) == 0) {
        // not a prime
        return -1;
      }
    }

    // check up to square root
    int m = (int) (Math.sqrt(p) / 30);
    for (int j = 1; j < m; j++) {
      for (int i = 0; i < 8; i++) {
        int d = 30 * j + ADD_CONST[i];
        if ((p % d) == 0) {
          // not a prime
          return -1;
        }
      }
    }

    // found a prime
    return p;
  }


  static {
    // start off with our personalization value
    setSeed(NonceFactory.personalization());

    ((ThreadPoolExecutor) FUTURE_RUNNER).allowCoreThreadTimeOut(true);
    ((ThreadPoolExecutor) ENTROPY_RUNNER).allowCoreThreadTimeOut(true);

    // Initialise the seed store in a random order. This makes it less
    // likely we will hit seed's created adjacently when we are looking for
    // any created seed early on.
    int[] p = permute(64);
    for (int i = 0; i < 64; i++) {
      Holder h = new Holder();
      STORE[p[i]] = h;
      h.reset();
    }

    // Seed storage may need random numbers, but now we are creating instant entropy, we can safely read our stored seed information confident that the
    // random number initialisation will go through.
    Seed seed;
    try (SeedStorage storage = SeedStorage.getInstance()) {
      seed = storage.get("instant");
    }
    if (seed != null) {
      setSeed(seed.getSeed());
    }
  }

  /**
   * This generator's ID
   */
  private final int id;

  /** Synchronizing latch */
  private final CountDownLatch latch;

  /** The entropy output sink */
  private final DigestDataOutput output;

  /**
   * Time this generator started
   */
  private final long startTime = System.nanoTime();


  /**
   * Create a thread that could generate some entropy
   *
   * @param id     an ID
   * @param latch  synchronization latch
   * @param output the output sink
   */
  InstantEntropy(int id, CountDownLatch latch, DigestDataOutput output) {
    this.id = id;
    this.latch = latch;
    this.output = output;
  }


  /**
   * Find prime numbers. As prime numbers are scattered without pattern and our starting points are from a cryptographically secure PRNG, the time it takes to
   * find such prime numbers should be difficult to predict. We also consider which thread produces the prime number, the number itself and the time it takes to
   * do so as useful entropy. <p>
   * <p>
   * Each execution of this method tests one number to see if it is prime. If it is not, this task is resubmitted. Otherwise, it writes out its entropy and
   * terminates.
   */
  @Override
  public void run() {
    synchronized (output) {
      output.writeBoolean(true);
      output.write(id);
      output.writeLong(Thread.currentThread().getId());
    }

    int p = tryFindPrime();
    if (p == -1) {
      // Did not find a prime this time, so resubmit
      ENTROPY_RUNNER.submit(this);
      return;
    }

    // We did find a prime
    long e = System.nanoTime() - startTime;

    // write out entropy
    synchronized (output) {
      output.writeBoolean(false);
      output.write(id);
      output.writeLong(Thread.currentThread().getId());
      output.writeInt(p);
      output.writeInt((int) e);
    }
    latch.countDown();
  }

}
