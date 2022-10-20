package prng.generator;

import java.security.DrbgParameters;
import java.security.DrbgParameters.Capability;
import java.security.SecureRandomParameters;
import java.security.SecureRandomSpi;

import prng.Fortuna;
import prng.OpenEngineSpi;

/**
 * Common NIST secure random number functionality.
 *
 * @author Simon Greatrix
 */
public abstract class BaseRandom extends SecureRandomSpi implements OpenEngineSpi {

  /** serial version UID */
  private static final long serialVersionUID = 1L;


  static byte[] getPersonalization(SecureRandomParameters parameters) {
    if (parameters instanceof DrbgParameters.Instantiation) {
      return ((DrbgParameters.Instantiation) parameters).getPersonalizationString();
    }
    if (parameters != null) {
      throw new IllegalArgumentException("Parameters was not an instance of DrbgParameters.Instantiation, but " + parameters.getClass());
    }
    return null;
  }


  static void verifyStrength(SecureRandomParameters parameters, int supported) {
    if (parameters instanceof DrbgParameters.Instantiation) {
      int required = ((DrbgParameters.Instantiation) parameters).getStrength();
      if (required > supported) {
        throw new IllegalArgumentException("Algorithm has a strength of " + supported + ", cannot supply " + required);
      }
    }
  }


  /** A copy of the personalization data. */
  protected final byte[] personalization;

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
   * @param source     source of seed information
   * @param initial    the initial material
   * @param resistance number of operations between re-seeds
   * @param seedSize   the number of bytes in a re-seed.
   * @param spareSize  the maximum space required for spare bytes
   */
  protected BaseRandom(SeedSource source, InitialMaterial initial, int resistance, int seedSize, int spareSize) {
    this.source = (source == null) ? Fortuna.SOURCE : source;
    this.initial = initial;
    this.resistance = resistance;
    this.seedSize = seedSize;
    personalization = initial.getPersonalization().clone();
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
  public final byte[] engineGenerateSeed(int size) {
    // Call our entropy source for a new seed.
    return source.getSeed(size);
  }


  @Override
  public SecureRandomParameters engineGetParameters() {
    checkInitialised();
    return DrbgParameters.instantiation(getStrength(), Capability.PR_AND_RESEED, initial.getPersonalization());
  }


  @Override
  public void engineNextBytes(byte[] bytes, SecureRandomParameters params) {
    checkInitialised();
    if (params instanceof DrbgParameters.NextBytes) {
      DrbgParameters.NextBytes nextBytes = (DrbgParameters.NextBytes) params;
      prepare(nextBytes.getPredictionResistance(), nextBytes.getAdditionalInput());
    }

    engineNextBytes(bytes);
  }


  @Override
  public final synchronized void engineNextBytes(byte[] bytes) {
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
  public void engineReseed(SecureRandomParameters params) {
    checkInitialised();

    if (params instanceof DrbgParameters.Reseed) {
      DrbgParameters.Reseed reseed = (DrbgParameters.Reseed) params;
      prepare(reseed.getPredictionResistance(), reseed.getAdditionalInput());
    } else if (params != null) {
      throw new UnsupportedOperationException("Cannot reseed from parameters of type: " + params.getClass());
    } else {
      // best effort
      implSetSeed(engineGenerateSeed(seedSize));
    }
  }


  @Override
  public final synchronized void engineSetSeed(byte[] seed) {
    checkInitialised();

    implSetSeed(seed);

    // reset the counter
    counter = 1;
  }


  protected abstract int getStrength();


  /**
   * The implementation for generating the next bytes, ignoring reseeds
   *
   * @param offset first byte to start
   * @param bytes  the bytes to generate
   */
  protected abstract void implNextBytes(int offset, byte[] bytes);


  /**
   * The implementation for updating the seed, ignoring reseed tracking
   *
   * @param seed the bytes to update with
   */
  protected abstract void implSetSeed(byte[] seed);


  /**
   * Initialise this instance with the provided material.
   *
   * @param material the starting material
   */
  protected abstract void initialise(byte[] material);


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
   * Apply the operations indicated by the SecureRandomParameters passed to reseed or next bytes.
   *
   * @param pr    if true, inject new entropy
   * @param input if specified, inject into the generator
   */
  private void prepare(boolean pr, byte[] input) {
    if (pr) {
      byte[] seed = engineGenerateSeed(seedSize);
      if (input != null) {
        byte[] newInput = new byte[seed.length + input.length];
        System.arraycopy(input, 0, newInput, 0, input.length);
        System.arraycopy(seed, 0, newInput, input.length, seed.length);
        input = newInput;
      } else {
        input = seed;
      }
    }
    if (input != null) {
      implSetSeed(input);
    }
  }


  /**
   * Set the spare bytes available for the next round
   *
   * @param data   the spares
   * @param offset the offset into the input
   * @param length the number of bytes
   */
  protected void setSpares(byte[] data, int offset, int length) {
    spareBytes = length;
    System.arraycopy(data, offset, spares, 0, length);
  }


  @Override
  public String toString() {
    return String.format(
        "%s(resistance=%d/%d, source=%s, spareBytes=%d)",
        getClass().getSimpleName(), counter, resistance, source, spareBytes
    );
  }

}
