package prng;

import java.security.SecureRandom;
import prng.generator.BaseRandom;

/**
 * Implementation of a SecureRandom using a given SPI.
 *
 * @author Simon Greatrix
 */
class SecureRandomImpl extends SecureRandom {

  /** serial version UID */
  private static final long serialVersionUID = 2l;

  /** The actual PRNG */
  private final BaseRandom base;


  /**
   * Create secure random instance
   *
   * @param spi SPI to use
   */
  SecureRandomImpl(BaseRandom spi) {
    super(spi, SecureRandomProvider.PROVIDER);
    base = spi;
  }


  /**
   * Get some material that can be used to re-seed this PRNG.
   *
   * @return some seed material
   */
  public byte[] newSeed() {
    return base.newSeed();
  }
}