package prng.seeds;

import prng.SecureRandomProvider;
import prng.generator.SeedSource;

/**
 * Generate fake seeds by generating every permutation of the number 0 to 255.
 *
 * @author Simon Greatrix
 */
public class PermutingSeedSource implements SeedSource {

  /** The numbers 0 to 255 in some order */
  byte[] data = new byte[256];

  /** Counts for Heap's algorithm */
  private short[] count = new short[256];

  /** State for Heap's algorithm */
  private int state = 0;


  /**
   * Create new seed source
   */
  public PermutingSeedSource() {
    // Ordering 3223 is the only order that scores the "best" for Runs test
    // on each individual bit. This corresponds to the LCG (101x+47)%256
    this(3223);
  }


  /**
   * Create new seed source. The order parameter specifies one of 8192 initial orderings. The number of possible initial orderings may change in future
   * releases.
   *
   * @param order specifier for the initial ordering
   */
  public PermutingSeedSource(int order) {
    // Use the order parameter to specify a full period linear congruential
    // generator.
    final int a = ((order >> 7) & 63) * 4 + 1;
    final int c = (order & 127) * 2 + 1;
    int j = 0;
    for (int i = 0; i < 256; i++) {
      count[i] = 0;
      data[i] = (byte) j;
      j = ((j * a) + c) & 0xff;
    }
  }


  @Override
  public byte[] getSeed(int size) {
    byte[] seed = new byte[size];
    int off = 0;
    while (size > 256) {
      System.arraycopy(data, 0, seed, off, 256);
      update();
      off += 256;
      size -= 256;
    }
    System.arraycopy(data, 0, seed, off, size);
    update();
    return seed;
  }


  /**
   * Update the permutation
   */
  protected void update() {
    // Implementation of Heap's Algorithm
    while (state < 256) {
      if (count[state] < state) {
        if ((state & 1) == 0) {
          byte t = data[0];
          data[0] = data[state];
          data[state] = t;
        } else {
          int i = count[state];
          byte t = data[state];
          data[state] = data[i];
          data[i] = t;
        }

        count[state]++;
        state = 0;

        return;
      }

      count[state] = 0;
      state++;
    }

    // All permutations have now been generated, so just start over.
    state = 0;
    for (int i = 0; i < 256; i++) {
      count[i] = 0;
    }

    // Note it should actually be physically impossible to get here within
    // the lifetime of the universe. At the time of writing the universe is
    // estimated to be approximately 10^26 nanoseconds old, but there are
    // over 10^500 permutations.
    SecureRandomProvider.LOG.info(
        "More than 10^500 permutations calculated. How come the universe still exists?");
    update();
  }

}
