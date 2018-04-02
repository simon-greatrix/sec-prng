package prng.generator;

import java.security.SecureRandomSpi;
import prng.Fortuna;

/**
 * Common NIST secure random number functionality.
 *
 * @author Simon Greatrix
 */
abstract public class BaseRandom extends SecureRandomSpi {

  /** serial version UID */
  private static final long serialVersionUID = 1L;

  /**
   * A counter for how often this can generate bytes before needing reseeding. Counter-intuitively, higher values of resistance are less secure.
   */
  private final int resistance;

  /**
   * Number of bytes required for a re-seed
   */
  private final int seedSize;

  /** Source of entropy */
  private final SeedSource source;

  /** Storage for spare bytes */
  private final byte[] spares;

  /** The initial material. */
  protected InitialMaterial initial;

  /**
   * The re-seed counter
   */
  private int counter = 0;

  /** Number of spare bytes currently available */
  private int spareBytes = 0;


  /**
   * New instance.
   *
   * @param source source of seed information
   * @param initial the initial material
   * @param resistance number of operations between re-seeds
   * @param seedSize the number of bytes in a re-seed.
   * @param spareSize the maximum space required for spare bytes
   */
  protected BaseRandom(SeedSource source, InitialMaterial initial,
      int resistance, int seedSize, int spareSize) {
    this.source = (source == null) ? Fortuna.SOURCE : source;
    this.initial = initial;
    this.resistance = resistance;
    this.seedSize = seedSize;
    spares = new byte[spareSize];
  }


  private void checkInitialised() {
    InitialMaterial myInitial = initial;
    if (myInitial != null) {
      initial = null;
      initialise(myInitial.combineMaterials());
    }
  }


  @Override
  protected final byte[] engineGenerateSeed(int size) {
    return source.getSeed(size);
  }


  @Override
  protected final synchronized void engineNextBytes(byte[] bytes) {
    checkInitialised();
    int offset = 0;
    if (spareBytes > 0) {
      int toUse = Math.min(spareBytes, bytes.length);
      System.arraycopy(spares, spareBytes - toUse, bytes, 0, toUse);
      spareBytes -= toUse;
      offset += toUse;
      if (offset == bytes.length) {
        return;
      }
    }
    if (resistance < counter) {
      engineSetSeed(engineGenerateSeed(seedSize));
    } else {
      counter++;
    }
    implNextBytes(offset, bytes);
  }


  @Override
  protected final synchronized void engineSetSeed(byte[] seed) {
    checkInitialised();

    implSetSeed(seed);

    // reset the counter
    counter = 1;
  }


  /**
   * The implementation for generating the next bytes, ignoring reseeds
   *
   * @param offset first byte to start
   * @param bytes the bytes to generate
   */
  abstract protected void implNextBytes(int offset, byte[] bytes);


  /**
   * The implementation for updating the seed, ignoring reseed tracking
   *
   * @param seed the bytes to update with
   */
  abstract protected void implSetSeed(byte[] seed);


  /**
   * Initialise this instance with the provided material.
   */
  abstract protected void initialise(byte[] material);


  /**
   * Create a value that can be used to seed this algorithm without loss of entropy
   *
   * @return a value for seeding this algorithm
   */
  public byte[] newSeed() {
    byte[] seed = new byte[seedSize];
    engineNextBytes(seed);
    return seed;
  }


  /**
   * Set the spare bytes available for the next round
   *
   * @param data the spares
   * @param offset the offset into the input
   * @param length the number of bytes
   */
  protected void setSpares(byte[] data, int offset, int length) {
    spareBytes = length;
    System.arraycopy(data, offset, spares, 0, length);
  }
}
