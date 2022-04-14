package prng;

import java.security.DigestException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import prng.collector.EntropyCollector;
import prng.collector.EntropyCollector.Fulfillment;
import prng.generator.SeedSource;
import prng.internet.NetManager;
import prng.seeds.DeferredSeed;
import prng.seeds.Seed;
import prng.seeds.SeedStorage;

/**
 * Implementation of a Fortuna-like secure random number source. Fortuna has many internal pools to collect potential entropy in. As long as some pool collects
 * some entropy, the output becomes unpredictable. <p>
 *
 * @author Simon Greatrix
 */
public class Fortuna {

  /**
   * Derive entropy from Fortuna
   */
  public static final SeedSource SOURCE = Fortuna::getSeed;



  /**
   * Lazily initialise Fortuna to minimise the risk of a circular dependency because ciphers need random numbers but Fortuna needs a cipher.
   */
  private static class InstanceHolder {

    /** The singleton instance of Fortuna */
    static final Fortuna INSTANCE;

    static {
      INSTANCE = new Fortuna();

      // Net manager requires HTTPS, which uses ciphers.
      NetManager.load();

      // Entropy collector uses random numbers
      EntropyCollector.restart();
    }
  }



  private static class Pool {

    /** A byte consisting of 4 randomly chosen bits that starts the digest stream. */
    private static final byte DIGEST_START = (byte) 0x6a;

    /** A byte, the complement of the DIGEST_START, that starts the seed stream. */
    private static final byte SEED_START = (byte) 0x95;

    private final MessageDigest digest;

    private final MessageDigest seed;

    private int count = 0;


    Pool() {
      try {
        digest = MessageDigest.getInstance("SHA-256");
        seed = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new InternalError("SHA-256 is required");
      }

      // Start the streams with different values so that the two do not match
      digest.update(DIGEST_START);
      seed.update(SEED_START);
    }


    byte[] fetch() {
      if (count < 55) {
        // insufficient entropy for one full SHA-256 block
        byte[] value = new byte[32];
        for (int i = 0; i < 32; i++) {
          value[i] = (byte) (count ^ (i * 59));
        }
        return value;
      }
      byte[] value = digest.digest();
      digest.update(value);
      count = 0;
      return value;
    }


    boolean hasEntropy() {
      // Enough entropy for one full SHA-256 block?
      return count >= 55;
    }


    void inject(byte[] data) {
      digest.update(data);
      seed.update(data);
      count += data.length;
    }


    byte[] seed() {
      byte[] value = new byte[64];
      try {
        digest.digest(value, 0, 32);
        seed.digest(value, 32, 32);
      } catch (DigestException e) {
        throw new InternalError("Digest failed on correct buffer size", e);
      }
      digest.update(DIGEST_START);
      digest.update(value);
      seed.update(SEED_START);
      seed.update(value);
      return value;
    }

  }



  /**
   * Get a seed from a random implementation
   *
   * @author Simon Greatrix
   */
  private static class SeedMaker implements Callable<byte[]> {

    /** The pool entry to make a seed for */
    final Pool impl;


    /**
     * New seed maker
     *
     * @param impl the random implementation
     */
    SeedMaker(Pool impl) {
      this.impl = impl;
    }


    @Override
    public byte[] call() {
      return impl.seed();
    }

  }


  /**
   * Add event data into the specified entropy pool
   *
   * @param pool the pool's ID
   * @param data the data
   */
  protected static void addEvent(int pool, byte[] data) {
    pool = pool & 31;
    Fortuna instance = InstanceHolder.INSTANCE;
    synchronized (instance) {
      Pool impl = instance.pool[pool];
      impl.inject(data);
      SeedStorage.enqueue(
          new DeferredSeed("Fortuna." + pool, new SeedMaker(impl)));
      instance.fulfillment.provided += data.length;
    }
  }


  /**
   * Get the ratio by which entropy requirements were fulfilled. Two special values may be returned: positive infinity to indicate that no entropy was used, and
   * negative infinity to indicate no entropy was added.
   *
   * @return fulfillment
   */
  public static Fulfillment getFulfillment() {
    Fortuna instance = InstanceHolder.INSTANCE;
    Fulfillment report = new Fulfillment();
    Fulfillment fulfillment = instance.fulfillment;
    synchronized (instance) {
      fulfillment.excess += fulfillment.provided - fulfillment.used;
      report.provided = fulfillment.provided;
      fulfillment.provided = 0;
      report.used = fulfillment.used;
      fulfillment.used = 0;
      report.excess = fulfillment.excess;
      return report;
    }
  }


  /**
   * Create a seed value
   *
   * @param bytes the number of bytes required
   *
   * @return the seed value
   */
  public static byte[] getSeed(int bytes) {
    Fortuna instance = InstanceHolder.INSTANCE;
    synchronized (instance) {
      return instance.randomData(bytes);
    }
  }


  /** A buffer to hold a single block */
  private final byte[] blockBuffer = new byte[16];

  /** An 128-bit counter */
  private final byte[] counter = new byte[16];

  /** Amount of entropy added since last reset. */
  private final Fulfillment fulfillment = new Fulfillment();

  /** Entropy accumulators */
  private final Pool[] pool = new Pool[32];

  /** AES with 256-bit key */
  private Cipher cipher;

  /** SHA-256 digest */
  private MessageDigest digest;

  /** A 256-bit cipher key */
  private byte[] key = new byte[32];

  /** Number of times this instance has been reseeded. */
  private int reseedCount = 0;


  /**
   * Create singleton instance
   */
  private Fortuna() {
    try {
      cipher = Cipher.getInstance("AES/ECB/NoPadding");
      digest = MessageDigest.getInstance("SHA-256");
    } catch (GeneralSecurityException gse) {
      throw new Error("Failed to initialise seed generator", gse);
    }

    // Create the entropy accumulators. These are based on NIST randoms,
    // with system entropy initially.
    byte[] entropy = new byte[128];
    for (int i = 0; i < 32; i++) {
      pool[i] = new Pool();
      SystemRandom.nextBytes(entropy);
      pool[i].inject(entropy);
    }

    // use our saved entropy for more buzz!
    try (SeedStorage store = SeedStorage.getInstance()) {
      for (int i = 0; i < 32; i++) {
        Seed seed = store.get("Fortuna." + i);
        if (seed != null) {
          pool[i].inject(seed.getSeed());
        }
      }
    }

    for (int i = 0; i < 32; i++) {
      SeedStorage.enqueue(new DeferredSeed("Fortuna." + i, new SeedMaker(pool[i])));
    }
  }


  /**
   * Internal function. Generate pseudo random data. The length must be a multiple of 16
   *
   * @param output output buffer
   * @param off    start of output in buffer
   * @param len    number of bytes to generate
   */
  private void generateBlocks(byte[] output, int off, int len) {
    try {
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
    } catch (InvalidKeyException e) {
      throw new Error(
          "AES cipher rejected key of " + key.length * 8 + " bits");
    }
    for (int pos = 0; pos < len; ) {
      try {
        pos += cipher.update(counter, 0, 16, output, pos);
        pos += cipher.doFinal(output, pos);
      } catch (GeneralSecurityException e) {
        throw new Error("Cipher failed", e);
      }
      incrementCounter();
    }
  }


  /**
   * Increment the counter
   */
  private void incrementCounter() {
    for (int i = 0; i < 16; i++) {
      byte b = counter[i];
      b++;
      counter[i] = b;
      if (b != 0) {
        break;
      }
    }
  }


  /**
   * Generate pseudo random data of the required size
   *
   * @param len the required size in bytes
   *
   * @return new random data
   */
  private byte[] pseudoRandomData(int len) {
    byte[] output = new byte[len];
    int runs = len / 1048576;
    int pos = 0;
    // generate at most 2^20=1048576 bytes at a time
    for (int i = 0; i < runs; i++) {
      generateBlocks(output, pos, 1048576);
      generateBlocks(key, 0, 32);
      pos += 1048576;
    }

    int finalLen = len - pos & 0x7ffffff0;
    if (finalLen > 0) {
      generateBlocks(output, pos, finalLen);
      pos += finalLen;
    }

    finalLen = len - pos;
    if (finalLen > 0) {
      byte[] buf = blockBuffer;
      generateBlocks(buf, 0, 16);
      System.arraycopy(buf, 0, output, pos, finalLen);
    }
    generateBlocks(key, 0, 32);
    return output;
  }


  /**
   * Prepare the generator for the next operation and create random data
   *
   * @param len number of bytes required
   *
   * @return random data
   */
  byte[] randomData(int len) {
    reseedCount++;

    // Check if pool 0 has enough entropy
    if (pool[0].hasEntropy()) {
      long rc = (long) reseedCount & 0xffffffffL;
      long mask = 1;
      int poolCount = 1;
      while ((mask & rc) == 0) {
        mask <<= 1;
        mask++;
        poolCount++;
      }
      byte[] seed = new byte[poolCount * 32];
      for (int i = 0; i < poolCount; i++) {
        byte[] buf = pool[i].fetch();
        System.arraycopy(buf, 0, seed, i * 32, 32);
      }
      reseed(seed);
    }
    synchronized (this) {
      // Should have had enough entropy to fulfill the request which would be 55 bytes into each pool.
      fulfillment.used += 32 * 55;
    }
    return pseudoRandomData(len);
  }


  /**
   * Insert additional seed data into this.
   *
   * @param input new seed data
   */
  private void reseed(byte[] input) {
    // derive new key
    digest.update(key);
    for (int i = 0; i < 32; i++) {
      key[i] = 0;
    }
    key = digest.digest(input);

    // increment counter
    incrementCounter();
  }

}
