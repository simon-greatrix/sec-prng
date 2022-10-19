package prng.seeds;

import prng.generator.SeedSource;

/**
 * All requested seeds consist wholly of zeros.
 *
 * @author Simon Greatrix
 */
public class ZeroSeedSource implements SeedSource {

  /**
   * A singleton source as all seeds are identical.
   */
  public static final SeedSource SOURCE = new ZeroSeedSource();


  @Override
  public String getName() {
    return "Zeros";
  }


  @Override
  public byte[] getSeed(int size) {
    return new byte[size];
  }

}
