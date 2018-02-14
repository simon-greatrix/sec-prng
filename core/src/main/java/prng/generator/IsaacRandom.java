package prng.generator;

import java.util.Arrays;
import java.util.Random;
import prng.SecureRandomProvider;

/**
 * The ISAAC random number generator is a cryptographically secure generator inspired by the RC4 cipher. This implementation incorporates the suggested changes
 * for the ISAAC+ algorithm. Additionally, when setSeed is called, the new seed is combined with the existing state so that entropy is augmented, not reset.
 *
 * @see <a href="http://en.wikipedia.org/wiki/ISAAC_%28cipher%29">ISAAC on Wikipedia</a>
 * @see <a href="http://eprint.iacr.org/2006/438.pdf">ISAAC+ algorithm</a>
 */
public class IsaacRandom extends Random {

  /** serial version UID */
  private static final long serialVersionUID = 1L;

  /** Architecture instance */
  private static IsaacRandom INSTANCE = null;


  /**
   * Create a starting seed. This seed can be considered to be cryptographically very weak.
   *
   * @param o an object to use as part of the seed
   *
   * @return a seed
   */
  private static int[] createStartingSeed(Object o) {
    int[] seed = new int[9];
    long l = System.nanoTime();
    seed[0] = (int) (l >>> 32);
    seed[1] = (int) l;
    l = System.currentTimeMillis();
    seed[2] = (int) (l >>> 32);
    seed[3] = (int) l;
    l = Runtime.getRuntime().freeMemory();
    seed[4] = (int) (l >>> 32);
    seed[5] = (int) l;

    seed[6] = System.identityHashCode(o);
    seed[7] = System.identityHashCode(IsaacRandom.class);
    seed[8] = System.identityHashCode(SecureRandomProvider.class);

    return seed;
  }


  /**
   * Get the shared ISAAC instance
   *
   * @return a instance that can be shared by different architecture components.
   */
  public static IsaacRandom getSharedInstance() {
    if (INSTANCE == null) {
      synchronized (IsaacRandom.class) {
        if (INSTANCE == null) {
          INSTANCE = new IsaacRandom();
        }
      }
    }
    return INSTANCE;
  }


  /**
   * Mix the bits of 8 integers
   *
   * @param s the 8 integers to mix
   */
  private static void mix(int[] s) {
    // @formatter:off
    s[0] ^= s[1] << 11;
    s[3] += s[0];
    s[1] += s[2];
    s[1] ^= s[2] >>> 2;
    s[4] += s[1];
    s[2] += s[3];
    s[2] ^= s[3] << 8;
    s[5] += s[2];
    s[3] += s[4];
    s[3] ^= s[4] >>> 16;
    s[6] += s[3];
    s[4] += s[5];
    s[4] ^= s[5] << 10;
    s[7] += s[4];
    s[5] += s[6];
    s[5] ^= s[6] >>> 4;
    s[0] += s[5];
    s[6] += s[7];
    s[6] ^= s[7] << 8;
    s[1] += s[6];
    s[7] += s[0];
    s[7] ^= s[0] >>> 9;
    s[2] += s[7];
    s[0] += s[1];
    // @formatter:on
  }

  /** Output of last generation */
  private final int[] randResult = new int[256];

  /** Internal generator state */
  private final int[] state = new int[256];

  /** Internal generator state */
  private int aa = 0, bb = 0, cc = 0;

  /** The number of values used from the current result */
  private int valuesUsed;


  /** Create a unseeded generator */
  public IsaacRandom() {
    super(0);
    setSeed(createStartingSeed(this));
  }


  /**
   * Create a generator with the specified seed
   *
   * @param seed the full seed
   */
  public IsaacRandom(int[] seed) {
    super(0);
    setSeed(seed);
  }


  /**
   * Create a generator using each character from the seed as one integer in the seed.
   *
   * @param seed the seed
   */
  public IsaacRandom(String seed) {
    super(0);
    setSeed(seed);
  }


  /**
   * Generate the next batch of random numbers
   */
  private void generateMoreResults() {
    // c <= c + 1
    cc++;

    // b <= b + c
    bb += cc;

    for (int i = 0; i < 256; i++) {
      // x <- state[i]
      int x = state[i];

      // a <- f(a,i) + state[i+128]
      switch (i & 3) {
        case 0:
          aa = aa ^ Integer.rotateLeft(aa, 13);
          break;
        case 1:
          aa = aa ^ Integer.rotateRight(aa, 6);
          break;
        case 2:
          aa = aa ^ Integer.rotateLeft(aa, 2);
          break;
        case 3:
          aa = aa ^ Integer.rotateRight(aa, 16);
          break;
      }
      aa = state[i ^ 128] + aa;

      // state[i] <- a + b + state[x>>2]
      // state[i] <- (a ^ b) + state[x>>2] (ISAAC+)
      int y = state[i] = state[(x >>> 2) & 0xFF] + (aa ^ bb);

      // r <- x + state[state[i]>>>10]
      // r <- x + a ^ state[state[i]>>>10] (ISAAC+)
      randResult[i] = bb = (aa ^ state[(y >>> 10) & 0xFF]) + x;
    }

    valuesUsed = 0;
  }


  /**
   * Initialise with a new seed.
   *
   * @param seed the new seed
   */
  private void init(int[] seed) {
    if (seed.length != 256) {
      seed = Arrays.copyOf(seed, 256);
    }

    // Combine with existing state. For the first time, this will be all
    // zero.
    if (valuesUsed > 0) {
      generateMoreResults();
    }
    for (int i = 0; i < 256; i++) {
      seed[i] = seed[i] ^ randResult[i];
    }

    // initialise to the golden ratio
    int[] initState = new int[8];
    Arrays.fill(initState, 0x9e3779b9);

    for (int i = 0; i < 4; i++) {
      mix(initState);
    }

    // mix the seed into the state
    for (int i = 0; i < 256; i += 8) {
      for (int j = 0; j < 8; j++) {
        initState[j] += seed[i + j];
      }
      mix(initState);
      for (int j = 0; j < 8; j++) {
        state[i + j] = initState[j];
      }
    }

    // mix the state with itself
    for (int i = 0; i < 256; i += 8) {
      for (int j = 0; j < 8; j++) {
        initState[j] += state[i + j];
      }
      mix(initState);
      for (int j = 0; j < 8; j++) {
        state[i + j] = initState[j];
      }
    }

    // Make sure generateMoreResults() will be called by
    // the next next() call.
    valuesUsed = 256;
  }


  @Override
  protected int next(int bits) {
    synchronized (randResult) {
      if (valuesUsed == 256) {
        generateMoreResults();
      }
      int value = randResult[valuesUsed];
      valuesUsed++;
      return value >>> (32 - bits);
    }
  }


  /**
   * Inject new entropy into this PRNG
   *
   * @param seed the new entropy, a 256 element integer array
   */
  public void setSeed(int[] seed) {
    if (seed == null) {
      // inject some entropy anyway
      setSeed(createStartingSeed(this));
      return;
    }
    synchronized (randResult) {
      super.setSeed(0);
      init(seed);
    }
  }


  /**
   * Inject new entropy into this PRNG
   *
   * @param seed the new entropy, a 64-bit long value
   */
  @Override
  public void setSeed(long seed) {
    if (state == null) {
      // We're being called from the superclass constructor. We don't
      // have our state arrays instantiated yet, and we're going to do
      // proper initialization later in our own constructor anyway, so
      // just ignore this call.
      super.setSeed(seed);
      return;
    }

    synchronized (randResult) {
      super.setSeed(0);
      int[] arraySeed = new int[256];
      arraySeed[0] = (int) seed;
      arraySeed[1] = (int) (seed >>> 32);
      init(arraySeed);
    }
  }


  /**
   * Inject new entropy into this PRNG
   *
   * @param seed a character string
   */
  public void setSeed(String seed) {
    synchronized (randResult) {
      super.setSeed(0);
      char[] charSeed = seed.toCharArray();
      int[] intSeed = new int[256];
      for (int i = 0; i < charSeed.length; i++) {
        intSeed[i & 0xff] += charSeed[i];
      }
      init(intSeed);
    }
  }

}
